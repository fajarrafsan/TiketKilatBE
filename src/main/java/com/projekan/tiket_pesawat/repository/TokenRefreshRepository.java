package com.projekan.tiket_pesawat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.TokenRefresh;

@Repository
public interface TokenRefreshRepository extends JpaRepository<TokenRefresh, Long>{
    Optional<TokenRefresh> findByToken(String token);
    void deleteByEmailPengguna(String emailPengguna);
    List<TokenRefresh> findByEmailPengguna(String emailPengguna);
}
