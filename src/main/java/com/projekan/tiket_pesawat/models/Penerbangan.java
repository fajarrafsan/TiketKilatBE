package com.projekan.tiket_pesawat.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Penerbangan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String maskapai;

    @Column(nullable = false, name = "kota_keberangkatan")
    private String kotaKeberangkatan;

    @Column(nullable = false, name = "kota_tujuan")
    private String kotaTujuan;

    @Column(nullable = false,name = "waktu_keberangkatan")
    private LocalDateTime waktuKeberangkatan;

    @Column(nullable = false, name = "waktu_kedatangan")
    private LocalDateTime waktuKedatangan;

    @Column(nullable = false, name = "harga_tiket")
    private BigDecimal hargaTiket;

    private Integer kursi;

    @Enumerated(EnumType.STRING)
    private KetersediaanPenerbangan ketersediaanPenerbangan;

    @Enumerated(EnumType.STRING)
    private StatusPenerbangan statusPenerbangan;

    @OneToMany(mappedBy = "penerbangan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UpdatePenerbanganHistory> histories = new ArrayList<>();

    @OneToMany(mappedBy = "penerbangan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Kursi> listKursi = new ArrayList<>();

    @OneToMany(mappedBy = "penerbangan",cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Tiket> listTiket = new ArrayList<>();

    @OneToMany(mappedBy = "penerbangan",cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Booking> listBooking = new ArrayList<>();

}
