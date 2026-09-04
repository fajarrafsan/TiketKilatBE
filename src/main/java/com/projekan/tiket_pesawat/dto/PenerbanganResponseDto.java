package com.projekan.tiket_pesawat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PenerbanganResponseDto {
    
    private Long id;
    private String maskapai;
    private String kotaKeberangkatan;
    private String kotaTujuan;
    private LocalDateTime waktuKeberangkatan;
    private LocalDateTime waktuKedatangan;
    private BigDecimal hargaTiket;
    
}
