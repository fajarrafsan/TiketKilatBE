package com.projekan.tiket_pesawat.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PemesananTiketRequestDto {
    @NotBlank(message = "Nama Tidak Boleh Kosong!")
    @Size(min = 2,max = 25,message = "Nama Harus terdiri dari 2 sampai 25 karakter")
    private String nama;

    @NotBlank(message = "Nomor HP Tidak boleh kosong!")
    @Pattern(regexp = "^08.*$",message = "Nomor HP harus diawali 08")
    @Size(min = 10,max = 15,message = "Nomor HP harus terdiri 10 hingga 15 digit angka")
    private String noHP;

    @NotNull(message = "ID penerbangan tidak boleh kosong")
    private Long penerbanganId;

    @NotNull(message = "File KTP wajib di unggah")
    private MultipartFile fileKtp;
}
