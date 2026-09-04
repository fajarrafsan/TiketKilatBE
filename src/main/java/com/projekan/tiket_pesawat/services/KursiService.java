package com.projekan.tiket_pesawat.services;

import java.util.List;

import com.projekan.tiket_pesawat.dto.KursiResponseDto;

public interface KursiService {
    List<KursiResponseDto> ambilDataKursiTersedia(String kodeBooking);
}
