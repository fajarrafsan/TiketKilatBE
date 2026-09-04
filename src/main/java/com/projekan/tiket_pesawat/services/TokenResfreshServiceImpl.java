package com.projekan.tiket_pesawat.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.projekan.tiket_pesawat.exception.TokenTidakDitemukan;
import com.projekan.tiket_pesawat.models.StatusTokenResfresh;
import com.projekan.tiket_pesawat.models.TokenRefresh;
import com.projekan.tiket_pesawat.repository.TokenRefreshRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenResfreshServiceImpl implements TokenRefreshService {
    private final TokenRefreshRepository tokenRefreshRepository;

    @Override
    public TokenRefresh buatTokenBaru(String email) {
        tokenRefreshRepository.findByEmailPengguna(email).forEach(token -> {
            token.setSudahDicabut(true);
            tokenRefreshRepository.save(token);
        });

        TokenRefresh tokenBaru = TokenRefresh.builder()
                .emailPengguna(email)
                .kadaluarsa(Instant.now().plus(7, ChronoUnit.DAYS))
                .token(UUID.randomUUID().toString())
                .sudahDicabut(false).build();
        return tokenRefreshRepository.save(tokenBaru);
    }

    @Override
    public Optional<TokenRefresh> cekTokenMasihInvalid(String tokenMasuk) {
        return tokenRefreshRepository.findByToken(tokenMasuk)
                .filter(t -> !t.isSudahDicabut() && t.getKadaluarsa().isAfter(Instant.now()));
    }

    @Override
    public void cabutToken(String tokenMasuk) {
        tokenRefreshRepository.findByToken(tokenMasuk).ifPresent(t -> {
            t.setSudahDicabut(true);
            tokenRefreshRepository.save(t);
        });
    }

    @Override
    public void logout(String tokenRefresh) {
        TokenRefresh token = tokenRefreshRepository.findByToken(tokenRefresh)
                .orElseThrow(
                        () -> new TokenTidakDitemukan("Token tidak ditemukan, silahkan cek token anda dengan benar"));

        if (token.getStatus() == StatusTokenResfresh.SUDAH_EXPIRED) {
            throw new TokenTidakDitemukan("Token sudah expired sebelumnya");
        }

        token.setStatus(StatusTokenResfresh.SUDAH_EXPIRED);
        token.setKadaluarsa(Instant.now());
        token.setSudahDicabut(true);
        tokenRefreshRepository.save(token);
    }
}
