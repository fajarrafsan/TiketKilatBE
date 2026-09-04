package com.projekan.tiket_pesawat.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.midtrans.Midtrans;

/** Server-to-server, read-only status lookup. Never trusts browser payment status. */
@Component
public class MidtransStatusClient {
    private final HttpClient client;

    public MidtransStatusClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    MidtransStatusClient(HttpClient client) {
        this.client = client;
    }

    public JSONObject check(String orderId) {
        String key = Midtrans.getServerKey();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Konfigurasi pembayaran backend belum tersedia.");
        }
        String base = Midtrans.isProduction ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v2/"
                + URLEncoder.encode(orderId, StandardCharsets.UTF_8) + "/status"))
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((key + ":").getBytes(StandardCharsets.UTF_8)))
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return new JSONObject().put("status_code", "404");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Status Midtrans belum dapat diperiksa. Cek konfigurasi backend atau coba lagi nanti.");
            }
            return new JSONObject(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pemeriksaan pembayaran terhenti.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Midtrans belum dapat dihubungi. Status pembayaran tidak diubah; coba lagi nanti.");
        } catch (org.json.JSONException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respons status Midtrans tidak valid.");
        }
    }
}
