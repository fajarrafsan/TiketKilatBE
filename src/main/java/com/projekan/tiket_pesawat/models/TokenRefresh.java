package com.projekan.tiket_pesawat.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TokenRefresh {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String token;

    private String emailPengguna;

    private Instant kadaluarsa;

    private boolean sudahDicabut;

    @Enumerated(EnumType.STRING)
    private StatusTokenResfresh status;

    @PrePersist
    @PreUpdate
    public void periksaStatus() {
        if (kadaluarsa.isBefore(Instant.now())) {
            this.status = StatusTokenResfresh.SUDAH_EXPIRED;
        } else if (sudahDicabut) {
            this.status = StatusTokenResfresh.SUDAH_DIPAKAI;
        } else {
            this.status = StatusTokenResfresh.MASUK_AKTIF;
        }
    }
}
