package com.projekan.tiket_pesawat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KursiResponseDto {
    private Long id;
    private String nomorKursi;
    private boolean kursiTersedia;
}
