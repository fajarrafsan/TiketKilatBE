package com.projekan.tiket_pesawat.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.repository.BookingRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentStatusUpdater {
    private final BookingRepository repository;
    private final EntityManager entityManager;

    public static void checkOwner(Booking booking, String email) {
        if (email == null || booking.getPenumpang() == null || booking.getPenumpang().getUser() == null
                || !email.equals(booking.getPenumpang().getUser().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pesanan bukan milik akun ini.");
        }
    }

    public static void checkOrder(Booking booking, String orderId) {
        if (orderId == null || !orderId.matches(Pattern.quote(booking.getKodeBooking()) + "-\\d{13}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID Midtrans tidak sesuai dengan kode booking.");
        }
        if (booking.getMidtransOrderId() != null && !booking.getMidtransOrderId().equals(orderId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order ID berbeda dari transaksi pesanan ini.");
        }
    }

    /** Called only with a response retrieved using the backend's Midtrans credentials. */
    @Transactional
    public Booking apply(String code, String orderId, JSONObject verified, String ownerEmail) {
        Booking booking = repository.findForUpdateByKodeBooking(code)
                .orElseThrow(() -> new TidakDitemukanException("Booking Tidak Ditemukan"));
        // OpenEntityManagerInView can retain the pre-network read; re-read under the lock.
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        if (ownerEmail != null) checkOwner(booking, ownerEmail);
        checkOrder(booking, orderId);
        if (!orderId.equals(verified.optString("order_id"))) invalid("Identitas transaksi Midtrans tidak cocok.");

        BigDecimal amount;
        try {
            amount = new BigDecimal(verified.get("gross_amount").toString());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nominal Midtrans tidak valid.");
        }
        if (booking.getTotalHarga() == null || amount.compareTo(booking.getTotalHarga()) != 0) {
            invalid("Nominal Midtrans tidak sesuai dengan pesanan.");
        }
        // The legacy Snap/Core API specifies gross_amount in IDR; older responses may omit currency.
        String currency = verified.optString("currency", "IDR");
        if (!"IDR".equals(currency)) invalid("Mata uang transaksi bukan IDR.");
        String status = verified.optString("transaction_status").toLowerCase(Locale.ROOT);
        if (status.isBlank()) invalid("Status Midtrans tidak lengkap.");
        String fraud = verified.optString("fraud_status", "").toLowerCase(Locale.ROOT);
        boolean paid = "200".equals(verified.optString("status_code")) && (
                ("settlement".equals(status) && (fraud.isBlank() || "accept".equals(fraud)))
                || ("capture".equals(status) && "credit_card".equals(verified.optString("payment_type"))
                        && "accept".equals(fraud)));

        boolean changed = booking.getMidtransOrderId() == null;
        booking.setMidtransOrderId(orderId);
        if (paid && booking.getStatusPembayaran() != StatusPembayaran.SUDAH_DIBAYAR) {
            // A verified payment can arrive after a local timer/cancellation; money received stays paid.
            booking.setStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
            booking.setWaktuPembayaran(LocalDateTime.now());
            changed = true;
        }
        // A failed Snap attempt may be retried under the same order. Cancellation/expiry
        // remains with the booking workflow; non-success responses cannot erase a payment.
        if (changed) repository.save(booking);
        return booking;
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
