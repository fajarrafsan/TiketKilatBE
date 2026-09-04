package com.projekan.tiket_pesawat.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.projekan.tiket_pesawat.dto.PenerbanganRingkasDto;
import com.projekan.tiket_pesawat.dto.HistoriPemesananDto;
import com.projekan.tiket_pesawat.dto.PenerbanganDto;
import com.projekan.tiket_pesawat.dto.PenerbanganUpdateDto;
import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.exception.AdminException;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import com.projekan.tiket_pesawat.models.Kursi;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.Penumpang;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.models.StatusPenerbangan;
import com.projekan.tiket_pesawat.models.Tiket;
import com.projekan.tiket_pesawat.repository.BookingRepository;
import com.projekan.tiket_pesawat.repository.KursiRepository;
import com.projekan.tiket_pesawat.repository.PenumpangRepository;
import com.projekan.tiket_pesawat.repository.PenerbanganRepository;
import com.projekan.tiket_pesawat.repository.TiketRepository;
import com.projekan.tiket_pesawat.services.AdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {
        private final PenerbanganRepository penerbanganRepository;
        private final AdminService adminService;
        private final KursiRepository kursiRepository;
        private final BookingRepository bookingRepository;
        private final TiketRepository tiketRepository;
        private final PenumpangRepository penumpangRepository;

        @PostMapping("tambah-penerbangan")
        public ResponseEntity<ResponseApi<?>> tambahPenerbangan(@RequestBody @Valid PenerbanganDto request) {

                if (request.getKotaKeberangkatan().equalsIgnoreCase(request.getKotaTujuan())) {
                        String errorNya = "Kota keberangkatan dan tujuan tidak boleh di tempat yang sama";
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("info ada kesalahan", errorNya,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (!request.getWaktuKedatangan().isAfter(request.getWaktuKeberangkatan())) {
                        String errorNya = "Waktu tiba harus setelah waktu keberangkatan";
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("info ada kesalahan", errorNya,
                                                        HttpStatus.BAD_REQUEST.value()));

                }

                int totalKursi = request.getKursi();
                List<Kursi> listKursi = new ArrayList<>();
                List<String> kursiNya = new ArrayList<>();

                Penerbangan penerbanganDataBaru = Penerbangan.builder()
                                .maskapai(request.getMaskapai())
                                .kotaKeberangkatan(request.getKotaKeberangkatan())
                                .kotaTujuan(request.getKotaTujuan())
                                .waktuKeberangkatan(request.getWaktuKeberangkatan())
                                .waktuKedatangan(request.getWaktuKedatangan())
                                .hargaTiket(request.getHargaTiket())
                                .kursi(request.getKursi())
                                .ketersediaanPenerbangan(KetersediaanPenerbangan.TERSEDIA)
                                .statusPenerbangan(StatusPenerbangan.ON_TIME)
                                .histories(null).build();

                int baris = 0;
                int colum = 0;

                for (int i = 1; i <= totalKursi; i++) {
                        char barisChar = (char) ('A' + baris);
                        colum++;

                        String nomorKursi = barisChar + String.valueOf(colum);
                        Kursi kursi = Kursi.builder()
                                        .nomorkursi(nomorKursi)
                                        .penerbangan(penerbanganDataBaru)
                                        .kursiTersedia(true)
                                        .build();
                        listKursi.add(kursi);
                        kursiNya.add(nomorKursi);

                        if (colum == 6) {
                                baris++;
                                colum = 0;
                        }

                }
                penerbanganRepository.save(penerbanganDataBaru);
                kursiRepository.saveAll(listKursi);
                Map<String, Object> response = Map.of(
                                "id", penerbanganDataBaru.getId(),
                                "maskapai", penerbanganDataBaru.getMaskapai(),
                                "kotaKeberangkatan", penerbanganDataBaru.getKotaKeberangkatan(),
                                "kotaTujuan", penerbanganDataBaru.getKotaTujuan(),
                                "waktuKeberangkatan", penerbanganDataBaru.getWaktuKeberangkatan().toString(),
                                "waktuKedatangan", penerbanganDataBaru.getWaktuKedatangan().toString(),
                                "hargaTiket", penerbanganDataBaru.getHargaTiket(),
                                "kursi", penerbanganDataBaru.getKursi(),
                                "list_kursi", kursiNya);
                return ResponseEntity
                                .ok(ResponseApi.sukses("Data Penerbangan Berhasil Di Tambahkan", response,
                                                HttpStatus.OK.value()));
        }

        @PutMapping("/update-data-penerbangan/{id}")
        public ResponseEntity<ResponseApi<?>> updateDataPenerbangan(@PathVariable Long id,
                        @RequestBody PenerbanganUpdateDto request,
                        @AuthenticationPrincipal UserDetails userNya) {
                String user = userNya.getUsername();
                Penerbangan penerbangan = penerbanganRepository.findById(id)
                                .orElseThrow(() -> new TidakDitemukanException(
                                                "Penerbangan dengan ID " + id + " Tidak ditemukan"));
                Map<String, Object> perubahan = adminService.updatePenerbangan(penerbangan, request, user);

                return ResponseEntity.ok(ResponseApi.sukses(
                                "Data Penerbangan Berhasil Di Update! dan di simpan di History Update", perubahan,
                                HttpStatus.OK.value()));
        }

        @GetMapping("/{penerbanganId}/ekspor-history-update-ke-excell")
        public ResponseEntity<?> exportUpdateKeExcell(@PathVariable Long penerbanganId) {
                try {
                        Penerbangan penerbangan = penerbanganRepository.findById(penerbanganId)
                                        .orElseThrow(() -> new TidakDitemukanException(
                                                        "Penerbangan dengan ID " + penerbanganId + " Tidak Ditemukan"));
                        ByteArrayInputStream fileExcell = adminService.eksportKeExcell(penerbangan);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentDisposition(ContentDisposition.builder("attachment")
                                        .filename("Data_Update_History ID " + penerbanganId + ".xlsx").build());

                        return ResponseEntity
                                        .ok()
                                        .headers(headers)
                                        .contentType(MediaType.parseMediaType(
                                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        .body(new InputStreamResource(fileExcell));
                } catch (IOException error) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ResponseApi.gagal("Info ada kesalahan", error.getMessage(),
                                                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
                } catch (TidakDitemukanException error) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                        ResponseApi.gagal(error.getMessage(), null, HttpStatus.NOT_FOUND.value()));
                } catch (IllegalStateException error) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                                        ResponseApi.gagal(error.getMessage(), null, HttpStatus.NOT_FOUND.value()));
                } catch (AdminException error) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                                        ResponseApi.gagal(error.getMessage(), null,
                                                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
                }
        }

        @DeleteMapping("/{penerbanganId}/hapus-penerbangan")
        public ResponseEntity<ResponseApi<?>> hapusDataPenerbangan(@PathVariable Long penerbanganId) {
                Penerbangan penerbangan = penerbanganRepository.findById(penerbanganId)
                                .orElseThrow(() -> new TidakDitemukanException("Data Penerbangan Tidak Di Temukan!"));

                boolean adaBookingTerkait = bookingRepository.findAll().stream()
                                .anyMatch(b -> b.getPenerbangan() != null
                                                && b.getPenerbangan().getId().equals(penerbanganId));
                if (adaBookingTerkait) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(ResponseApi.gagal(
                                        "Penerbangan tidak dapat dihapus karena masih memiliki pemesanan terhubung", null,
                                        HttpStatus.CONFLICT.value()));
                }

                Map<String, Object> dataTerhapus = Map.of(
                                "id", penerbangan.getId(),
                                "maskapai", penerbangan.getMaskapai(),
                                "kotaKeberangkatan", penerbangan.getKotaKeberangkatan(),
                                "kotaTujuan", penerbangan.getKotaTujuan());
                penerbanganRepository.delete(penerbangan);

                return ResponseEntity
                                .ok(ResponseApi.sukses("Data Berhasil Di hapus", dataTerhapus,
                                                HttpStatus.OK.value()));
        }

        @GetMapping("/Mengambil-semua-data-penerbangan")
        public ResponseEntity<ResponseApi<?>> tampilkanDataPenerbangan(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "waktuKeberangkatan") String urutanBerdasarkan,
                        @RequestParam(defaultValue = "asc") String arah) {
                Page<PenerbanganRingkasDto> data = adminService
                                .ambilDataPenerbangan(page, size, urutanBerdasarkan, arah)
                                .map(PenerbanganRingkasDto::dari);

                return ResponseEntity.ok(ResponseApi.sukses("Data Berhasil DiTampilkan", data, HttpStatus.OK.value()));
        }

        @GetMapping("/histori-pemesanan")
        public ResponseEntity<?> getHistoriPemesanan(
                        @RequestParam(required = false) String maskapai,
                        @RequestParam(required = false) StatusPembayaran status,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal) {
                List<Booking> listBooking = bookingRepository.findFiltered(maskapai, status, tanggal);

                List<HistoriPemesananDto> result = listBooking.stream().map(booking -> {
                        Penerbangan penerbangan = booking.getPenerbangan();
                        Penumpang penumpang = booking.getPenumpang();

                        return new HistoriPemesananDto(
                                        penumpang.getNama(),
                                        penumpang.getUser().getEmail(),
                                        booking.getKodeBooking(),
                                        penerbangan.getMaskapai(),
                                        penerbangan.getKotaKeberangkatan(),
                                        penerbangan.getKotaTujuan(),
                                        penerbangan.getWaktuKeberangkatan(),
                                        penerbangan.getWaktuKedatangan(),
                                        booking.getTotalHarga(),
                                        booking.getStatusPembayaran(),
                                        booking.getWaktuBooking());
                }).collect(Collectors.toList());

                return ResponseEntity.ok(ResponseApi.sukses("Histori Pemesanan Tiket", result, HttpStatus.OK.value()));
        }

        @GetMapping("/penerbangan/{id}")
        public ResponseEntity<ResponseApi<?>> detailPenerbangan(@PathVariable Long id) {
                Penerbangan penerbangan = penerbanganRepository.findById(id)
                                .orElseThrow(() -> new TidakDitemukanException(
                                                "Penerbangan dengan ID " + id + " Tidak ditemukan"));
                List<Kursi> listKursi = kursiRepository.findByPenerbanganId(id);
                long kursiTersedia = listKursi.stream().filter(Kursi::isKursiTersedia).count();

                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("id", penerbangan.getId());
                data.put("maskapai", penerbangan.getMaskapai());
                data.put("kotaKeberangkatan", penerbangan.getKotaKeberangkatan());
                data.put("kotaTujuan", penerbangan.getKotaTujuan());
                data.put("waktuKeberangkatan", penerbangan.getWaktuKeberangkatan().toString());
                data.put("waktuKedatangan", penerbangan.getWaktuKedatangan().toString());
                data.put("hargaTiket", penerbangan.getHargaTiket());
                data.put("kursi", penerbangan.getKursi());
                data.put("kursiTersedia", kursiTersedia);
                data.put("kursiTerisi", listKursi.size() - kursiTersedia);
                data.put("ketersediaanPenerbangan", penerbangan.getKetersediaanPenerbangan().name());
                data.put("statusPenerbangan", penerbangan.getStatusPenerbangan().name());

                return ResponseEntity.ok(ResponseApi.sukses("Detail Penerbangan", data, HttpStatus.OK.value()));
        }

        @GetMapping("/penumpang-per-penerbangan/{penerbanganId}")
        public ResponseEntity<?> daftarPenumpangPerPenerbangan(@PathVariable Long penerbanganId) {
                penerbanganRepository.findById(penerbanganId)
                                .orElseThrow(() -> new TidakDitemukanException("Penerbangan Tidak Ditemukan"));
                List<Booking> bookings = bookingRepository.findAll().stream()
                                .filter(b -> b.getPenerbangan() != null
                                                && b.getPenerbangan().getId().equals(penerbanganId))
                                .collect(Collectors.toList());

                List<Map<String, Object>> result = bookings.stream().map(booking -> {
                        // LinkedHashMap, bukan Map.of: pesanan tanpa tiket punya nomorKursi null.
                        Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("namaPenumpang", booking.getPenumpang().getNama());
                        item.put("noHP", booking.getPenumpang().getNoHP());
                        item.put("email", booking.getPenumpang().getUser() != null
                                        ? booking.getPenumpang().getUser().getEmail() : null);
                        item.put("kodeBooking", booking.getKodeBooking());
                        item.put("nomorKursi", booking.getTiker() != null && booking.getTiker().getKursi() != null
                                        ? booking.getTiker().getKursi().getNomorkursi() : null);
                        item.put("statusPembayaran", booking.getStatusPembayaran().name());
                        return item;
                }).collect(Collectors.toList());

                return ResponseEntity.ok(ResponseApi.sukses("Daftar Penumpang", result, HttpStatus.OK.value()));
        }

        @GetMapping("/dashboard/stats")
        public ResponseEntity<?> dashboardStats() {
                long totalPenerbangan = penerbanganRepository.count();
                long penerbanganTersedia = penerbanganRepository.countByKetersediaanPenerbangan(KetersediaanPenerbangan.TERSEDIA);
                long penerbanganBerangkat = penerbanganRepository.countByStatusPenerbangan(StatusPenerbangan.DEPARTED);
                long totalBooking = bookingRepository.count();
                long bookingDibayar = bookingRepository.countByStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
                long bookingBerlalu = bookingRepository.countByStatusPembayaran(StatusPembayaran.CANCEL);
                long totalTiket = tiketRepository.count();

                Map<String, Object> data = Map.of(
                                "totalPenerbangan", totalPenerbangan,
                                "penerbanganTersedia", penerbanganTersedia,
                                "penerbanganBerangkat", penerbanganBerangkat,
                                "totalBooking", totalBooking,
                                "bookingDibayar", bookingDibayar,
                                "bookingDibatalkan", bookingBerlalu,
                                "totalTiketTerjual", totalTiket);

                return ResponseEntity.ok(ResponseApi.sukses("Statistik Dashboard", data, HttpStatus.OK.value()));
        }

        @Transactional
        @PostMapping("/booking/{kodeBooking}/cancel")
        public ResponseEntity<?> cancelBookingByAdmin(@PathVariable String kodeBooking) {
                Booking booking = bookingRepository.findForUpdateByKodeBooking(kodeBooking)
                                .orElseThrow(() -> new TidakDitemukanException("Booking Tidak Ditemukan"));

                if (booking.getStatusPembayaran() == StatusPembayaran.SUDAH_DIBAYAR) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Booking yang sudah dibayar tidak bisa dibatalkan tanpa proses refund", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (booking.getStatusPembayaran() == StatusPembayaran.CANCEL) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Booking sudah dibatalkan", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (booking.getTiker() != null) {
                        booking.getTiker().getKursi().setKursiTersedia(true);
                        kursiRepository.save(booking.getTiker().getKursi());
                        tiketRepository.delete(booking.getTiker());
                }

                booking.setStatusPembayaran(StatusPembayaran.CANCEL);
                bookingRepository.save(booking);
                return ResponseEntity.ok(ResponseApi.sukses("Booking Berhasil Dibatalkan", null, HttpStatus.OK.value()));
        }
}
