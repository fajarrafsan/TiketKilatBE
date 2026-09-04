package com.projekan.tiket_pesawat.controllers;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class HalamanWeb {

    private final BookingRepository bookingRepository;

    @GetMapping("/{kodeBooking}/halaman-verifikasi-pembayaran")
    public ResponseEntity<ResponseApi<Map<String, Object>>> tampilanHalamanVerifikasi(
            @PathVariable String kodeBooking, Principal principal) {
        try {
            Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(
                    () -> new TidakDitemukanException("Booking dengan ID : " + kodeBooking + " Tidak di Temukan"));

            if (principal == null || booking.getPenumpang() == null || booking.getPenumpang().getUser() == null
                    || !booking.getPenumpang().getUser().getEmail().equals(principal.getName())) {
                return ResponseEntity.status(403)
                        .body(ResponseApi.gagal("Akses Di Tolak", null, 403));
            }

            // LinkedHashMap, bukan Map.of: waktuPembayaran masih null selama belum lunas.
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("kodeBooking", booking.getKodeBooking());
            data.put("statusPembayaran", booking.getStatusPembayaran());
            data.put("waktuPembayaran",
                    booking.getWaktuPembayaran() != null ? booking.getWaktuPembayaran().toString() : null);

            return ResponseEntity.ok(ResponseApi.sukses("Data booking ditemukan", data, 200));
        } catch (TidakDitemukanException error) {
            return ResponseEntity.status(404)
                    .body(ResponseApi.gagal(error.getMessage(), null, 404));
        }
    }
}
