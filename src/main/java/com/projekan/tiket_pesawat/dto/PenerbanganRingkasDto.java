package com.projekan.tiket_pesawat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.projekan.tiket_pesawat.models.Penerbangan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bentuk ringkas Penerbangan untuk daftar admin.
 *
 * Entity Penerbangan tidak boleh dikirim langsung ke JSON: relasi listKursi,
 * listBooking, dan listTiket menunjuk balik ke Penerbangan sehingga Jackson
 * akan berputar tanpa henti, dan listBooking ikut membawa data User termasuk
 * hash password.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PenerbanganRingkasDto {
    private Long id;
    private String maskapai;
    private String kotaKeberangkatan;
    private String kotaTujuan;
    private LocalDateTime waktuKeberangkatan;
    private LocalDateTime waktuKedatangan;
    private BigDecimal hargaTiket;
    private Integer kursi;
    private String ketersediaanPenerbangan;
    private String statusPenerbangan;

    public static PenerbanganRingkasDto dari(Penerbangan penerbangan) {
        return PenerbanganRingkasDto.builder()
                .id(penerbangan.getId())
                .maskapai(penerbangan.getMaskapai())
                .kotaKeberangkatan(penerbangan.getKotaKeberangkatan())
                .kotaTujuan(penerbangan.getKotaTujuan())
                .waktuKeberangkatan(penerbangan.getWaktuKeberangkatan())
                .waktuKedatangan(penerbangan.getWaktuKedatangan())
                .hargaTiket(penerbangan.getHargaTiket())
                .kursi(penerbangan.getKursi())
                .ketersediaanPenerbangan(penerbangan.getKetersediaanPenerbangan() != null
                        ? penerbangan.getKetersediaanPenerbangan().name() : null)
                .statusPenerbangan(penerbangan.getStatusPenerbangan() != null
                        ? penerbangan.getStatusPenerbangan().name() : null)
                .build();
    }
}
