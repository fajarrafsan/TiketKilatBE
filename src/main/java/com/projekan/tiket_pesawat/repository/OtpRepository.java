package com.projekan.tiket_pesawat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.OTP;
import com.projekan.tiket_pesawat.models.StatusOtp;

@Repository
public interface OtpRepository extends JpaRepository<OTP, Long>{
    Optional<OTP> findTopByEmailAndStatusOrderByAwalBuatKodeDesc(String email,StatusOtp status);
    Optional<OTP> findByEmailAndKodeOtp(String email, String kodeOtp);
}
