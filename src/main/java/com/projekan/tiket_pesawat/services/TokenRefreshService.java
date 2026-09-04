package com.projekan.tiket_pesawat.services;

import java.util.Optional;
import com.projekan.tiket_pesawat.models.TokenRefresh;

public interface TokenRefreshService {
    TokenRefresh buatTokenBaru(String email);
    Optional<TokenRefresh> cekTokenMasihInvalid(String tokenMasuk);
    void cabutToken(String tokenMasuk);
    void logout(String tokenRefresh);
}
