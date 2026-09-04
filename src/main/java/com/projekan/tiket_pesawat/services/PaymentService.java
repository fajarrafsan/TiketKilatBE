package com.projekan.tiket_pesawat.services;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.midtrans.Midtrans;
import com.midtrans.httpclient.SnapApi;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final MidtransStatusClient statusClient;
    private final PaymentStatusUpdater statusUpdater;

    public Map<String, String> buatTransaksiSnap(String kodeBooking) {
        String kunci = Midtrans.getServerKey();
        if (kunci == null || kunci.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pembayaran belum aktif karena konfigurasi Midtrans di backend belum benar. Hubungi pengelola.");
        }

        Booking booking = bookingRepository.findByKodeBooking(kodeBooking)
                        .orElseThrow(() -> new TidakDitemukanException("Booking Tidak Ditemukan"));

        if (booking.getStatusPembayaran() == StatusPembayaran.SUDAH_DIBAYAR) {
            throw new IllegalStateException("Booking sudah dibayar");
        }
        if (booking.getStatusPembayaran() == StatusPembayaran.CANCEL) {
            throw new IllegalStateException("Booking sudah dibatalkan");
        }

        // Transaksi yang sudah pernah dibuat dipakai ulang, bukan dibuat lagi, supaya
        // pesanan yang sama tidak menghasilkan dua Order ID di Midtrans.
        if (booking.getSnapToken() != null && booking.getMidtransOrderId() != null) {
            return ringkasanPembayaran(booking);
        }
        if (booking.getMidtransOrderId() != null) {
            throw new IllegalStateException(
                    "Transaksi pembayaran untuk pesanan ini sudah dibuat sebelumnya, tetapi tokennya tidak tersimpan. "
                            + "Periksa status dengan Order ID dari dashboard Midtrans; jangan bayar ulang.");
        }

        String urutan = kodeBooking + "-" + System.currentTimeMillis();
        try {
            long amount = booking.getTotalHarga().longValueExact();
            if (amount <= 0) throw new IllegalArgumentException("Nominal pembayaran harus positif dalam rupiah utuh");
            booking.setMidtransOrderId(urutan);
            bookingRepository.saveAndFlush(booking);
            Map<String, Object> parameter = Map.of(
                            "transaction_details", Map.of(
                                            "order_id", urutan,
                                            "gross_amount", amount),
                            "customer_details", Map.of(
                                            "first_name", booking.getPenumpang().getNama(),
                                            "email", booking.getPenumpang().getUser().getEmail(),
                                            "phone", booking.getPenumpang().getNoHP()),
                            "item_details", List.of(Map.of(
                                            "id", String.valueOf(booking.getPenerbangan().getId()),
                                            "price", amount,
                                            "quantity", 1,
                                            "name", "Tiket " + booking.getPenerbangan().getKotaKeberangkatan() + " - "
                                                            + booking.getPenerbangan().getKotaTujuan())));

            JSONObject transaksi = SnapApi.createTransaction(parameter);
            booking.setSnapToken(transaksi.getString("token"));
            booking.setSnapRedirectUrl(transaksi.getString("redirect_url"));
            bookingRepository.saveAndFlush(booking);

            return ringkasanPembayaran(booking);
        } catch (Exception e) {
            // Snap gagal berarti tidak ada transaksi nyata di Midtrans. Order ID dilepas
            // kembali agar pesanan tidak tersangkut dan masih bisa dicoba ulang.
            lepasOrderIdGagal(kodeBooking, urutan);
            throw new IllegalStateException("Gagal membuat transaksi pembayaran: " + e.getMessage(), e);
        }
    }

    /** Token Snap milik pesanan yang belum lunas, untuk dilanjutkan dari tab atau perangkat lain. */
    public Map<String, String> ambilTransaksiSnap(String kodeBooking, String ownerEmail) {
        Booking booking = bookingRepository.findByKodeBooking(kodeBooking)
                        .orElseThrow(() -> new TidakDitemukanException("Booking Tidak Ditemukan"));
        PaymentStatusUpdater.checkOwner(booking, ownerEmail);

        if (booking.getStatusPembayaran() == StatusPembayaran.SUDAH_DIBAYAR) {
            throw new IllegalStateException("Booking sudah dibayar");
        }
        if (booking.getStatusPembayaran() == StatusPembayaran.CANCEL) {
            throw new IllegalStateException("Booking sudah dibatalkan");
        }
        if (booking.getSnapToken() == null || booking.getMidtransOrderId() == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Token pembayaran untuk pesanan ini belum tersimpan. Periksa status dengan Order ID dari dashboard Midtrans; jangan bayar ulang.");
        }
        return ringkasanPembayaran(booking);
    }

    private Map<String, String> ringkasanPembayaran(Booking booking) {
        return Map.of(
                        "kodeBooking", booking.getKodeBooking(),
                        "orderId", booking.getMidtransOrderId(),
                        "snapToken", booking.getSnapToken(),
                        "redirectUrl", booking.getSnapRedirectUrl(),
                        "totalHarga", booking.getTotalHarga().toPlainString());
    }

    private void lepasOrderIdGagal(String kodeBooking, String orderId) {
        try {
            bookingRepository.findByKodeBooking(kodeBooking).ifPresent(terkini -> {
                if (orderId.equals(terkini.getMidtransOrderId()) && terkini.getSnapToken() == null) {
                    terkini.setMidtransOrderId(null);
                    bookingRepository.saveAndFlush(terkini);
                }
            });
        } catch (RuntimeException ignored) {
            // Kegagalan pelepasan tidak boleh menutupi penyebab asli kegagalan pembayaran.
        }
    }

    public Booking syncPayment(String code, String ownerEmail, String requestedOrderId) {
        Booking booking = bookingRepository.findByKodeBooking(code)
                .orElseThrow(() -> new TidakDitemukanException("Booking Tidak Ditemukan"));
        PaymentStatusUpdater.checkOwner(booking, ownerEmail);
        if (booking.getStatusPembayaran() == StatusPembayaran.SUDAH_DIBAYAR) return booking;
        String orderId = requestedOrderId == null || requestedOrderId.isBlank()
                ? booking.getMidtransOrderId() : requestedOrderId.trim();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Order ID Midtrans belum tersimpan untuk pesanan lama ini. Buka pembayaran yang sama atau masukkan Order ID dari dashboard Midtrans; jangan bayar ulang.");
        }
        PaymentStatusUpdater.checkOrder(booking, orderId);
        JSONObject verified = statusClient.check(orderId);
        // A Snap token can exist before a payment method is selected; 404 does not mean canceled.
        if ("404".equals(verified.optString("status_code"))) return booking;
        return statusUpdater.apply(code, orderId, verified, ownerEmail);
    }

    public void prosesNotifikasi(JSONObject notifikasi) {
        String orderId = notifikasi.optString("order_id", null);
        String statusKode = notifikasi.optString("status_code", null);
        String grossAmount = notifikasi.optString("gross_amount", null);
        String signatureKey = notifikasi.optString("signature_key", null);

        if (!verifikasiSignature(orderId, statusKode, grossAmount, signatureKey)) {
            throw new IllegalStateException("Signature Midtrans tidak valid");
        }

        if (!orderId.matches("ASTRA-[A-F0-9]{8}-\\d{13}")) {
            throw new IllegalArgumentException("Order ID Midtrans tidak valid");
        }
        String code = orderId.substring(0, orderId.lastIndexOf('-'));
        // Signature authenticates the notification, GET status provides the current authoritative state.
        JSONObject verified = statusClient.check(orderId);
        if ("404".equals(verified.optString("status_code"))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Status notifikasi Midtrans belum tersedia. Silakan kirim ulang notifikasi.");
        }
        statusUpdater.apply(code, orderId, verified, null);
    }

    private boolean verifikasiSignature(String orderId, String statusKode, String grossAmount, String signatureKey) {
        if (orderId == null || statusKode == null || grossAmount == null || signatureKey == null) {
            return false;
        }
        String data = orderId + statusKode + grossAmount + Midtrans.getServerKey();
        String kalkulasi = sha512(data);
        return kalkulasi.equalsIgnoreCase(signatureKey);
    }

    private String sha512(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membuat hash SHA512", e);
        }
    }
}
