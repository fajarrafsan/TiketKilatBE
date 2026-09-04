package com.projekan.tiket_pesawat.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseApi<T> {
    private boolean sukses;
    private String pesanNya;
    private T data;
    private int statusKode;
    private LocalDateTime stempelWaktu;

    public static <T> ResponseApi<T> sukses(String pesan, T data, int statusKode) {
        return ResponseApi.<T>builder()
                .sukses(true)
                .pesanNya(pesan)
                .data(data)
                .statusKode(statusKode)
                .stempelWaktu(LocalDateTime.now())
                .build();
    }

    public static <T> ResponseApi<T> gagal(String pesan, T data, int statusKode) {
        return ResponseApi.<T>builder()
                .sukses(false)
                .pesanNya(pesan)
                .data(data)
                .statusKode(statusKode)
                .stempelWaktu(LocalDateTime.now())
                .build();
    }

}
