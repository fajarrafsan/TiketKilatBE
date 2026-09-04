package com.projekan.tiket_pesawat.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PembayaranTiketSchedule {

    private final BookingRepository bookingRepository;

    @Scheduled(fixedRate = 60000)
    public void cekBookingKadaluarsa(){
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookingan = bookingRepository.findByStatusPembayaranAndBatasWaktuPembayaranBefore(StatusPembayaran.BELUM_DIBAYAR, now);

        for (Booking booking : bookingan) {
            // The payment may have been confirmed after the list was read. Never overwrite it.
            int changed = bookingRepository.expireIfStillUnpaid(booking.getId(), now,
                    StatusPembayaran.BELUM_DIBAYAR, StatusPembayaran.CANCEL);
            if (changed == 1) {
                System.out.println("Booking ID: "+ booking.getId() + " dibatalkan otomatis, karena melebihi batas waktu pembayaran");
            }
        }

    }

}
