package com.projekan.tiket_pesawat.services;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.projekan.tiket_pesawat.models.OTP;
import com.projekan.tiket_pesawat.models.StatusOtp;
import com.projekan.tiket_pesawat.repository.OtpRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;

    @Override
    public String buatOtp() {
        Random randomkode = new Random();
        int kodeOtp = 100000 + randomkode.nextInt(900000);
        return String.valueOf(kodeOtp);
    }

    @Override
    public void kirimOtp(String email) {
        String otpKode = buatOtp();
        OTP otp = OTP.builder()
                .email(email)
                .kodeOtp(otpKode)
                .status(StatusOtp.AKTIF)
                .awalBuatKode(LocalDateTime.now())
                .expired(LocalDateTime.now().plusMinutes(5))
                .build();
        otpRepository.save(otp);
        emailService.kirimOtp(email, otpKode);
    }

    @Override
    public boolean verifikasiOtp(String email, String kode) {
        Optional<OTP> kodeOtp = otpRepository.findByEmailAndKodeOtp(email, kode);
        if (kodeOtp.isPresent()) {
            OTP otp = kodeOtp.get();
            if (otp.getExpired().isBefore(LocalDateTime.now())) {
                otp.setStatus(StatusOtp.EXPIRED);
                otpRepository.save(otp);
                return false;
            }
            otp.setStatus(StatusOtp.DIVERIFIKASI);
            otpRepository.save(otp);
            return true;
        }
        return false;
    }

}
