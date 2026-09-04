package com.projekan.tiket_pesawat.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.exception.AdminException;
import com.projekan.tiket_pesawat.exception.EmailException;
import com.projekan.tiket_pesawat.exception.EmailTidakDitemukan;
import com.projekan.tiket_pesawat.exception.StatusTidakValidException;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.exception.TokenTidakDitemukan;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class ErrorController {

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ResponseApi<?>> handlePaymentStatusError(ResponseStatusException error) {
                return ResponseEntity.status(error.getStatusCode())
                                .body(ResponseApi.gagal(error.getReason(), null, error.getStatusCode().value()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ResponseApi<Map<String, String>>> validasiException(MethodArgumentNotValidException e) {
                Map<String, String> errorNya = new HashMap<>();
                e.getBindingResult().getAllErrors().forEach(error -> {
                        String namaField = ((FieldError) error).getField();
                        String message = error.getDefaultMessage();
                        errorNya.put(namaField, message);
                });

                // Frontend hanya menampilkan pesanNya, jadi rincian per-field ikut diringkas
                // di sana; map lengkapnya tetap dikirim di data.
                ResponseApi<Map<String, String>> responseApi = ResponseApi.gagal(
                                ringkasValidasi(errorNya),
                                errorNya, HttpStatus.BAD_REQUEST.value());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseApi);
        }

        @ExceptionHandler(BindException.class)
        public ResponseEntity<ResponseApi<Map<String, String>>> validasiBindException(BindException e) {
                Map<String, String> errorNya = new HashMap<>();
                e.getBindingResult().getFieldErrors()
                                .forEach(error -> errorNya.put(error.getField(), error.getDefaultMessage()));

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseApi.gagal(
                                ringkasValidasi(errorNya), errorNya, HttpStatus.BAD_REQUEST.value()));
        }

        private String ringkasValidasi(Map<String, String> errorNya) {
                if (errorNya.isEmpty()) return "Info ada kesalahan, Validasi gagal";
                return "Validasi gagal: " + errorNya.entrySet().stream()
                                .map(entry -> entry.getKey() + " - " + entry.getValue())
                                .collect(java.util.stream.Collectors.joining("; "));
        }

        @ExceptionHandler(EmailException.class)
        public ResponseEntity<ResponseApi<?>> handleEmailException(EmailException error) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ResponseApi.gagal("Info ada Kesalahan Pada Email", error.getMessage(),
                                                HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }

        @ExceptionHandler(AdminException.class)
        public ResponseEntity<ResponseApi<?>> handleAdminException(AdminException error) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }

        @ExceptionHandler(ExpiredJwtException.class)
        public ResponseEntity<ResponseApi<?>> handleExpiredToken(ExpiredJwtException error) {
                Map<String, String> response = Map.of("pesan-error",
                                "Tokennya sudah Expired!, silahkan refresh token anda",
                                "error-Nya",
                                error.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ResponseApi.gagal("Info ada kesalahan!", response,
                                                HttpStatus.UNAUTHORIZED.value()));
        }

        @ExceptionHandler(JwtException.class)
        public ResponseEntity<ResponseApi<?>> handleJwtException(JwtException error) {
                Map<String, String> response = Map.of("pesan-error",
                                "Token anda Tidak valid, silahkan di cek lebih lanjut",
                                "error-Nya",
                                error.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ResponseApi.gagal("Info ada kesalahan!", response,
                                                HttpStatus.UNAUTHORIZED.value()));
        }

        @ExceptionHandler(EmailTidakDitemukan.class)
        public ResponseEntity<ResponseApi<?>> handleEmailTidakDitemukan(EmailTidakDitemukan error) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.UNAUTHORIZED.value()));
        }

        @ExceptionHandler(TokenTidakDitemukan.class)
        public ResponseEntity<ResponseApi<?>> handleTokenTidakDitemukan(TokenTidakDitemukan error) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.UNAUTHORIZED.value()));
        }

        @ExceptionHandler(TidakDitemukanException.class)
        public ResponseEntity<ResponseApi<?>> handleTidakDitemukan(TidakDitemukanException error) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.NOT_FOUND.value()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ResponseApi<?>> handleBadRequest(IllegalArgumentException error) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.BAD_REQUEST.value()));
        }
        @ExceptionHandler(StatusTidakValidException.class)
        public ResponseEntity<ResponseApi<?>> handleStatusTidakValid(StatusTidakValidException error) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ResponseApi.gagal("Info ada kesalahan!", error.getMessage(),
                                                HttpStatus.BAD_REQUEST.value()));
        }

        // Dipakai antara lain saat pembuatan transaksi Midtrans gagal. Tanpa handler ini
        // responsnya jatuh ke halaman error bawaan Spring yang tidak punya pesanNya,
        // sehingga frontend hanya bisa menampilkan pesan generik.
        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<ResponseApi<?>> handleIllegalState(IllegalStateException error) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ResponseApi.gagal(error.getMessage(), null, HttpStatus.CONFLICT.value()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ResponseApi<?>> handleUmum(Exception error) {
                // Kesalahan MVC bawaan (404 rute tidak dikenal, 405 method salah, dan
                // sejenisnya) sudah membawa status sendiri; jangan diubah jadi 500.
                if (error instanceof ErrorResponse bawaan) {
                        return ResponseEntity.status(bawaan.getStatusCode())
                                        .body(ResponseApi.gagal(error.getMessage(), null,
                                                        bawaan.getStatusCode().value()));
                }

                log.error("Kesalahan tidak tertangani", error);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ResponseApi.gagal(
                                                "Terjadi kesalahan di server. Silakan coba lagi atau hubungi pengelola.",
                                                null, HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }

        
}
