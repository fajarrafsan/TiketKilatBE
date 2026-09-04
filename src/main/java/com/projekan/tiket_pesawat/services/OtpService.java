package com.projekan.tiket_pesawat.services;

public interface OtpService {
    void kirimOtp(String email);
    boolean verifikasiOtp(String email, String kode);
    String buatOtp();
}
