package com.projekan.tiket_pesawat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PenerbanganDto {
    @Schema(description = "Nama Maskapai penerbangan", nullable = true)
    @NotBlank(message = "Nama maskapai tidak boleh kosong, Wajib di isi")
    private String maskapai;

    @Schema(description = "Kota keberangkatan", nullable = true) 
    @NotBlank(message = "Kota Keberangkatan tidak boleh kosong, Wajib di isi")
    private String kotaKeberangkatan;

    @Schema(description = "Kota Tujuan", nullable = true)
    @NotBlank(message = "Kota Tujuan tidak boleh kosong, Wajib di isi")
    private String kotaTujuan;

    @Schema(description = "Waktu keberangkatan", nullable = true)
    @Future(message = "Waktu keberangkatan harus di masa depan")
    private LocalDateTime waktuKeberangkatan;

    @Schema(description = "Waktu Kedatangan", nullable = true)
    @Future(message = "Waktu tiba harus di masa depan")
    private LocalDateTime waktuKedatangan;

    @Schema(description = "Harga Tiket", nullable = true)
    @DecimalMin(value = "0.0", inclusive = false, message = "Harga harus lebih besar dari 0 dan tidak boleh negatif")
    private BigDecimal hargaTiket;
    
    @Schema(description = "Jumlah Kursi Tersedia", nullable = true)
    @Min(value = 1, message = "Jumlah Kursi harus minimal 1")
    private Integer kursi;
}
