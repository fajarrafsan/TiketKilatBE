package com.projekan.tiket_pesawat.services;

public interface EmailService {
    void kirimToken(String email, String verifikasiToken , String refreshToken);
    void kirimOtp(String email,String otpCode);
    void kirimKonfirmasiBooking(String email, String kodeBooking, String namaPenumpang, String maskapai,
            String dari, String ke, String waktuKeberangkatan, String totalHarga, String batasWaktuPembayaran);
    void kirimTiket(String email, String kodeBooking, String namaPenumpang, String maskapai,
            String dari, String ke, String waktuKeberangkatan, String nomorKursi);
}
