package com.projekan.tiket_pesawat.controllers;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.services.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/notification")
    public ResponseEntity<?> handleNotification(@RequestBody String notifikasi) {
        try {
            JSONObject payload = new JSONObject(notifikasi);
            paymentService.prosesNotifikasi(payload);
            return ResponseEntity.ok(ResponseApi.sukses("Notifikasi diproses", null, HttpStatus.OK.value()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                            .body(ResponseApi.gagal(e.getReason(), null, e.getStatusCode().value()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                            .body(ResponseApi.gagal(e.getMessage(), null, HttpStatus.BAD_REQUEST.value()));
        }
    }
}
