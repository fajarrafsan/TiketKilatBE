package com.projekan.tiket_pesawat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.projekan.tiket_pesawat.models.Role;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AdminUserConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default-email}")
    private String adminEmail;

    @Value("${admin.default-password}")
    private String adminPassword;

    @Bean
    public CommandLineRunner admin() {
        return args -> {
            if (adminPassword == null || adminPassword.isBlank()) {
                System.out.println("ADMIN_PASSWORD belum di-set, lewati pembuatan admin default.");
                return;
            }
            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                                .email(adminEmail)
                                .password(passwordEncoder.encode(adminPassword))
                                .role(Role.ADMIN)
                                .build();
                userRepository.save(admin);
                System.out.println("Pengguna Admin Berhasil Dibuat: " + adminEmail);
            }
        };
    }
}
