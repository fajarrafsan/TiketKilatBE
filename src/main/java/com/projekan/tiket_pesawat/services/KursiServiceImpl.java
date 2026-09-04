package com.projekan.tiket_pesawat.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.projekan.tiket_pesawat.dto.KursiResponseDto;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.Kursi;
import com.projekan.tiket_pesawat.repository.BookingRepository;
import com.projekan.tiket_pesawat.repository.KursiRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KursiServiceImpl implements KursiService {

    private final KursiRepository kursiRepository;
    private final BookingRepository bookingRepository;

    @Override
    public List<KursiResponseDto> ambilDataKursiTersedia(String kodeBooking) {
        Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(()-> new TidakDitemukanException("Kode Booking Tidak Di Temukan")); 
        Long penerbanganId = booking.getPenerbangan().getId();
        List<Kursi> listKursi =  kursiRepository.findAvailableByPenerbangan(penerbanganId);
        return listKursi.stream()
                    .map(kursi -> KursiResponseDto.builder()
                            .id(kursi.getId())
                            .nomorKursi(kursi.getNomorkursi())
                            .kursiTersedia(kursi.isKursiTersedia())
                            .build())
                    .collect(Collectors.toList());
    }
}
