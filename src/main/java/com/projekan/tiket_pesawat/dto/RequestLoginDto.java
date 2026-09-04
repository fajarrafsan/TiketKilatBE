package com.projekan.tiket_pesawat.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RequestLoginDto {
    @NotBlank(message = "Email tidak boleh kosong")
    @Email(message = "Email Tidak Sesuai Format")
    @Schema(example = "fajar.rafsan01@gmail.com")
    private String email;
    @NotBlank(message = "Password tidak boleh kosong")
    @Schema(example = "Admin12345")
    private String password;
}
