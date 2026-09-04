package com.projekan.tiket_pesawat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PenerbanganUpdateDto {
    @Schema(description = "Nama Maskapai penerbangan", nullable = true, example = "null")
    private String maskapai;
    @Schema(description = "Kota keberangkatan", nullable = true, example = "null")
    private String kotaKeberangkatan;
    @Schema(description = "Kota Tujuan", nullable = true, example = "null")
    private String kotaTujuan;
    @Schema(description = "Waktu keberangkatan", nullable = true, example = "null")
    private LocalDateTime waktuKeberangkatan;
    @Schema(description = "Waktu Kedatangan", nullable = true, example = "null")
    private LocalDateTime waktuKedatangan;
    @Schema(description = "Harga Tiket", nullable = true, example = "null")
    private BigDecimal hargaTiket;
    @Schema(description = "Jumlah Kursi Tersedia", nullable = true, example = "null")
    private Integer kursi;
}
