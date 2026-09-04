package com.projekan.tiket_pesawat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.projekan.tiket_pesawat.models.StatusPembayaran;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistoriPemesananDto {
    private String namaPenumpang;
    private String emailUser;
    private String kodeBooking;
    private String maskapai;
    private String kotaKeberangkatan;
    private String kotaTujuan;
    private LocalDateTime waktuKeberangkatan;
    private LocalDateTime waktuKedatangan;
    private BigDecimal totalHarga;
    private StatusPembayaran statusPembayaran;
    private LocalDateTime waktuBooking;
}
