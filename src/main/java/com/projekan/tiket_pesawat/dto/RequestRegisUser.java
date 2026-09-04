package com.projekan.tiket_pesawat.dto;

import com.projekan.tiket_pesawat.models.Role;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RequestRegisUser {
    @NotBlank(message = "Email tidak boleh kosong")
    @Email(message = "Email Tidak Sesuai Format")
    private String email;
    private String nama;
    @NotBlank(message = "Password tidak boleh kosong")
    @Size(min = 5, message = "Password minimal 5 karakter")
    @Pattern(regexp = "^[A-Z].*", message = "Password harus dimulai dengan huruf kapital")
    @Pattern(regexp = ".*[a-z].*", message = "Password harus memiliki minimal satu huruf kecil setelah huruf kapital")
    @Pattern(regexp = ".*\\d{3}$", message = "Password harus diakhiri dengan 3 angka")
    private String password;
    @Enumerated(EnumType.STRING)
    private Role role;
}
