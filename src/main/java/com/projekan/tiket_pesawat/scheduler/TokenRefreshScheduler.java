package com.projekan.tiket_pesawat.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.projekan.tiket_pesawat.models.StatusTokenResfresh;
import com.projekan.tiket_pesawat.models.TokenRefresh;
import com.projekan.tiket_pesawat.repository.TokenRefreshRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenRefreshScheduler {

    private final TokenRefreshRepository tokenRefreshRepository;

    @PostConstruct
    public void eksekusiSaatStartUp() {
        System.out.println("Mengecek dan membersihkan token saat Run");

        perbaruiTokenRefresh();
        hapusTokenKadaluarsa();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void perbaruiTokenRefresh() {
        List<TokenRefresh> semuaToken = tokenRefreshRepository.findAll();
        for (TokenRefresh token : semuaToken) {
            boolean expired = token.getKadaluarsa().isBefore(Instant.now());
            boolean dicabut = token.isSudahDicabut();

            if (dicabut) {
                token.setStatus(StatusTokenResfresh.SUDAH_DIPAKAI);
            } else if (expired) {
                token.setStatus(StatusTokenResfresh.SUDAH_EXPIRED);
            } else {
                token.setStatus(StatusTokenResfresh.MASUK_AKTIF);
            }
            tokenRefreshRepository.save(token);
        }
        System.out.println("Status token Sudah Diperbarui secara otomatis");
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void hapusTokenKadaluarsa() {
        List<TokenRefresh> semuaToken = tokenRefreshRepository.findAll();
        for (TokenRefresh token : semuaToken) {
            if (token.getStatus() == StatusTokenResfresh.SUDAH_EXPIRED || token.getStatus() == StatusTokenResfresh.SUDAH_DIPAKAI) {
                tokenRefreshRepository.delete(token);
            }
        }
        System.out.println("Token yang expired berhasil dihapus");
    }
}
