package com.projekan.tiket_pesawat.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.projekan.tiket_pesawat.exception.EmailException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    @Value("${spring.mail.username}")
    private String asalEmail;

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender javaMailSender;

    private static final String LOKASI_TEMPLATE = "templates/html/";

    private String prosesTemplate(String namaTemplate, Map<String, String> variabel) {
        try (InputStream aliran = new ClassPathResource(LOKASI_TEMPLATE + namaTemplate + ".html").getInputStream()) {
            String html = new String(aliran.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : variabel.entrySet()) {
                html = html.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
            }
            return html;
        } catch (IOException e) {
            logger.error("Gagal membaca template email {}: {}", namaTemplate, e.getMessage());
            throw new EmailException("Gagal memproses template email");
        }
    }

    private String escapeHtml(String nilai) {
        if (nilai == null) {
            return "";
        }
        return nilai.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public void kirimToken(String email, String verifikasiToken, String refreshToken) {
        try {
            String htmlKonten = prosesTemplate("template_email",
                    Map.of("email", email, "token", verifikasiToken, "refreshToken", refreshToken));
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setPriority(1);
            helper.setSubject("Kode Akses Aman Untuk Verifikasi Anda");
            helper.setFrom(asalEmail);
            helper.setTo(email);
            helper.setText(htmlKonten, true);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Gagal mengirim email ke {}: {}", email, e.getMessage());
            throw new EmailException("Gagal mengirim email ke " + email + "Silahkan coba lagi.");
        } catch (Exception e) {
            logger.error("Kesalahan tak terduga saat megirim Email ke {}: {}", email, e);
            throw new EmailException("Kesalahan internal saat mengirim email");
        }
    }

    @Override
    public void kirimOtp(String email, String kodeOtp) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            String htmlKonten = prosesTemplate("template_kirim_otp",
                    Map.of("otp", kodeOtp,
                            "waktuKode", "5",
                            "waktu_sekarang",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm"))));
            helper.setPriority(1);
            helper.setTo(email);
            helper.setFrom(asalEmail);
            helper.setSubject("Kode OTP - Untuk lupa password");
            helper.setText(htmlKonten, true);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Gagal mengirim email ke {}: {}", email, e.getMessage());
            throw new EmailException("Gagal mengirim email ke " + email + "Silahkan coba lagi.");
        } catch (Exception e) {
            logger.error("Kesalahan tak terduga saat megirim Email ke {}: {}", email, e);
            throw new EmailException("Kesalahan internal saat mengirim email");
        }
    }

    @Override
    public void kirimKonfirmasiBooking(String email, String kodeBooking, String namaPenumpang, String maskapai,
            String dari, String ke, String waktuKeberangkatan, String totalHarga, String batasWaktuPembayaran) {
        try {
            String htmlKonten = prosesTemplate("template_konfirmasi_booking",
                    Map.of(
                            "kodeBooking", kodeBooking,
                            "namaPenumpang", namaPenumpang,
                            "maskapai", maskapai,
                            "dari", dari,
                            "ke", ke,
                            "waktuKeberangkatan", waktuKeberangkatan,
                            "totalHarga", totalHarga,
                            "batasWaktuPembayaran", batasWaktuPembayaran));
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setPriority(1);
            helper.setSubject("Konfirmasi Booking Tiket - " + kodeBooking);
            helper.setFrom(asalEmail);
            helper.setTo(email);
            helper.setText(htmlKonten, true);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Gagal mengirim email konfirmasi booking ke {}: {}", email, e.getMessage());
            throw new EmailException("Gagal mengirim email ke " + email + "Silahkan coba lagi.");
        } catch (Exception e) {
            logger.error("Kesalahan tak terduga saat megirim Email ke {}: {}", email, e);
            throw new EmailException("Kesalahan internal saat mengirim email");
        }
    }

    @Override
    public void kirimTiket(String email, String kodeBooking, String namaPenumpang, String maskapai,
            String dari, String ke, String waktuKeberangkatan, String nomorKursi) {
        try {
            String htmlKonten = prosesTemplate("template_tiket",
                    Map.of(
                            "kodeBooking", kodeBooking,
                            "namaPenumpang", namaPenumpang,
                            "maskapai", maskapai,
                            "dari", dari,
                            "ke", ke,
                            "waktuKeberangkatan", waktuKeberangkatan,
                            "nomorKursi", nomorKursi));
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setPriority(1);
            helper.setSubject("Tiket Anda - " + kodeBooking);
            helper.setFrom(asalEmail);
            helper.setTo(email);
            helper.setText(htmlKonten, true);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Gagal mengirim email tiket ke {}: {}", email, e.getMessage());
            throw new EmailException("Gagal mengirim email ke " + email + "Silahkan coba lagi.");
        } catch (Exception e) {
            logger.error("Kesalahan tak terduga saat megirim Email ke {}: {}", email, e);
            throw new EmailException("Kesalahan internal saat mengirim email");
        }
    }
}
