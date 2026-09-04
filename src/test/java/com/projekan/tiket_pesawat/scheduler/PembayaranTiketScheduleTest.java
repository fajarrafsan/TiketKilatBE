package com.projekan.tiket_pesawat.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class PembayaranTiketScheduleTest {
    @Mock private BookingRepository repository;

    @Test
    void expirationUsesConditionalUpdateNeverSavingTheStaleList() {
        Booking booking = Booking.builder().id(17L).statusPembayaran(StatusPembayaran.SUDAH_DIBAYAR).build();
        when(repository.findByStatusPembayaranAndBatasWaktuPembayaranBefore(eq(StatusPembayaran.BELUM_DIBAYAR), any()))
                .thenReturn(List.of(booking));
        new PembayaranTiketSchedule(repository).cekBookingKadaluarsa();
        verify(repository).expireIfStillUnpaid(eq(17L), any(), eq(StatusPembayaran.BELUM_DIBAYAR), eq(StatusPembayaran.CANCEL));
        verify(repository, never()).saveAll(any());
        verify(repository, never()).save(any());
        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
    }
}
