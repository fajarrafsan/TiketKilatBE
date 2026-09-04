package com.projekan.tiket_pesawat.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.projekan.tiket_pesawat.dto.PenerbanganResponseDto;
import com.projekan.tiket_pesawat.dto.PenerbanganUpdateDto;
import com.projekan.tiket_pesawat.models.Penerbangan;

public interface AdminService {
    Map<String, Object> updatePenerbangan(Penerbangan penerbangan, PenerbanganUpdateDto request, String userNya);
    ByteArrayInputStream eksportKeExcell(Penerbangan penerbangan) throws IOException;
    Page<Penerbangan> ambilDataPenerbangan(int page, int size, String urutBerdasarkan, String arah);
    List<PenerbanganResponseDto> ambilPenerbanganTersedia(String dari, String ke, LocalDate tanggal, String maskapai);
}
