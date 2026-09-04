package com.projekan.tiket_pesawat.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
// import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.projekan.tiket_pesawat.dto.KursiResponseDto;
import com.projekan.tiket_pesawat.dto.PemesananTiketRequestDto;
import com.projekan.tiket_pesawat.dto.PenerbanganResponseDto;
import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import com.projekan.tiket_pesawat.models.Kursi;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.Penumpang;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.models.StatusPenerbangan;
import com.projekan.tiket_pesawat.models.Tiket;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.BookingRepository;
import com.projekan.tiket_pesawat.repository.KursiRepository;
import com.projekan.tiket_pesawat.repository.PenerbanganRepository;
import com.projekan.tiket_pesawat.repository.PenumpangRepository;
import com.projekan.tiket_pesawat.repository.TiketRepository;
import com.projekan.tiket_pesawat.repository.UserRepository;
import com.projekan.tiket_pesawat.services.AdminService;
import com.projekan.tiket_pesawat.services.EmailService;
import com.projekan.tiket_pesawat.services.KursiService;
import com.projekan.tiket_pesawat.services.PaymentService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

        private final PenerbanganRepository penerbanganRepository;
        private final TiketRepository tiketRepository;
        private final KursiRepository kursiRepository;
        private final PenumpangRepository penumpangRepository;
        private final BookingRepository bookingRepository;
        private final UserRepository userRepository;
        private final AdminService adminService;
        private final KursiService kursiService;
        private final PasswordEncoder passwordEncoder;
        private final EmailService emailService;
        private final PaymentService paymentService;

        @Value("${app.base-url}")
        private String baseUrl;

        @PostMapping(value = "/pemesanan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Pesan Tiket Pesawat", description = "Cek Dulu Penerbangan yang aktif")
        public ResponseEntity<?> buatPesananTiket(@ModelAttribute @Valid PemesananTiketRequestDto pemesanan,
                        Principal principal)
                        throws IOException {
                String email = principal.getName();
                User user = userRepository.findByEmail(email).orElseThrow();
                Penerbangan penerbangan = penerbanganRepository.findById(pemesanan.getPenerbanganId())
                                .orElseThrow(() -> new TidakDitemukanException("Data Penerbangan Tidak di Temukan"));

                if (penerbangan.getStatusPenerbangan().equals(StatusPenerbangan.DEPARTED)) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal(
                                                        "Penerbangan dengan ID : " + penerbangan.getId()
                                                                        + "tidak dapat dilayani karena sudah berangkat",
                                                        null, HttpStatus.BAD_REQUEST.value()));
                } else if (penerbangan.getStatusPenerbangan().equals(StatusPenerbangan.ARRIVED)) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Penerbangan dengan ID : " + penerbangan.getId()
                                                        + "tidak dapat dilayani karena penerbangan telah selesai", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                boolean semuaKursiSudahTerisi = kursiRepository.findByPenerbanganId(penerbangan.getId())
                                .stream()
                                .noneMatch(kursi -> kursi.isKursiTersedia());

                if (semuaKursiSudahTerisi) {
                        penerbangan.setKetersediaanPenerbangan(KetersediaanPenerbangan.TIDAK_TERSEDIA);
                        penerbanganRepository.save(penerbangan);

                        return ResponseEntity.badRequest().body(ResponseApi.gagal(
                                        "Maaf Penerbangan Tersebut Tidak Tersedia, Mungkin tiket Sudah Habis", null,
                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (pemesanan.getFileKtp() == null || pemesanan.getFileKtp().isEmpty()) {
                        return ResponseEntity.badRequest().body(ResponseApi.gagal(
                                        "File KTP wajib di upload", null, HttpStatus.BAD_REQUEST.value()));
                }

                String contentType = pemesanan.getFileKtp().getContentType();
                long ukuranFile = pemesanan.getFileKtp().getSize();

                if (!List.of("image/jpeg", "image/png", "application/pdf").contains(contentType)) {
                        return ResponseEntity.badRequest().body(ResponseApi.gagal(
                                        "Format file KTP harus JPG, PNG, atau PDF", null, HttpStatus.BAD_REQUEST.value()));
                }

                if (ukuranFile > 2 * 1024 * 1024) {
                        return ResponseEntity.badRequest().body(ResponseApi.gagal(
                                        "Ukuran file KTP maksimal 2MB", null, HttpStatus.BAD_REQUEST.value()));
                }

                String namaFile = UUID.randomUUID() + "_" + pemesanan.getFileKtp().getOriginalFilename();
                Path path = Paths.get("uploads/ktp/" + namaFile);
                Files.createDirectories(path.getParent());
                Files.write(path, pemesanan.getFileKtp().getBytes());

                BigDecimal hargaTiket = penerbangan.getHargaTiket();

                Penumpang penumpang = Penumpang.builder()
                                .nama(pemesanan.getNama())
                                .noHP(pemesanan.getNoHP())
                                .user(user)
                                .fileKtp(namaFile).build();

                String kodeBooking = "ASTRA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

                Booking booking = Booking.builder()
                                .totalHarga(hargaTiket)
                                .kodeBooking(kodeBooking)
                                .penerbangan(penerbangan)
                                .penumpang(penumpang)
                                .statusPembayaran(StatusPembayaran.BELUM_DIBAYAR)
                                .waktuBooking(LocalDateTime.now())
                                .waktuPembayaran(null)
                                .batasWaktuPembayaran(LocalDateTime.now().plusMinutes(15)).build();

                penumpangRepository.save(penumpang);
                bookingRepository.save(booking);

                emailService.kirimKonfirmasiBooking(
                                user.getEmail(),
                                booking.getKodeBooking(),
                                penumpang.getNama(),
                                penerbangan.getMaskapai(),
                                penerbangan.getKotaKeberangkatan(),
                                penerbangan.getKotaTujuan(),
                                penerbangan.getWaktuKeberangkatan().toString(),
                                "Rp " + hargaTiket.toString(),
                                booking.getBatasWaktuPembayaran().toString());

                Map<String, String> pembayaran = paymentService.buatTransaksiSnap(booking.getKodeBooking());

                return ResponseEntity.ok(ResponseApi.sukses("Pesanan Berhasil Disimpan, Silahkan Melakukan Pembayaran",
                                pembayaran, HttpStatus.OK.value()));
        }

        @GetMapping("/{kodeBooking}/verifikasi-pembayaran")
        public ResponseEntity<?> linkPembayaran(@PathVariable String kodeBooking, Principal principal) {
                Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(
                                () -> new TidakDitemukanException(
                                                "Booking dengan Kode Booking : " + kodeBooking + " Tidak di Temukan"));

                String email = principal.getName();
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new TidakDitemukanException("user Tidak di Temukan"));

                if (booking.getPenumpang() == null || booking.getPenumpang().getUser() == null
                                || !booking.getPenumpang().getUser().equals(user)) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Akses Di Tolak", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                String linkHtml = baseUrl + "/user/" + booking.getKodeBooking() + "/halaman-verifikasi-pembayaran";

                Map<String, String> response = Map.of("pesan",
                                "Klik link di bawah ini di browser untuk verifikasi pembayaran:",
                                "url", linkHtml);

                return ResponseEntity.ok(ResponseApi.sukses("Link verifikasi pembayaran", response,
                                HttpStatus.OK.value()));
        }

        @PostMapping("/pemilihan-nomor-kursi")
        @Operation(summary = "Buat Pilih Nomor Kursi", description = "Silahkan Cek Dulu Kursi yang Tersedia, Sebelum Memilih Kursi Id")
        public ResponseEntity<?> pilihNomorKursi(@RequestParam String kodeBooking, @RequestParam Long kursiId,
                        Principal principal) {
                String email = principal.getName();
                User user = userRepository.findByEmail(email).orElseThrow();

                Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(
                                () -> new TidakDitemukanException(
                                                "Booking dengan Kode Booking : " + kodeBooking + " Tidak di Temukan"));

                if (booking.getPenumpang() == null || booking.getPenumpang().getUser() == null
                                || !booking.getPenumpang().getUser().equals(user)) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Info ada Kesalahan, Sesuaikan dengan Id User nya",
                                                        null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (booking.getStatusPembayaran() != StatusPembayaran.SUDAH_DIBAYAR) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal(
                                                        "Pembayaran Belum Selesai, Silahkan Selesaikan Terlebih Dahulu Pembayaran anda!",
                                                        null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (tiketRepository.existsByBooking(booking)) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Booking Ini Sudah Digunakan Untuk Tiket!", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                Kursi kursi = kursiRepository.findById(kursiId)
                                .orElseThrow(() -> new TidakDitemukanException("Data Kursi Tidak Di Temukan"));
                if (!kursi.isKursiTersedia()) {
                        return ResponseEntity.badRequest().body(ResponseApi.gagal("Nomor Kursi Sudah Digunakan", null,
                                        HttpStatus.BAD_REQUEST.value()));
                }

                kursi.setKursiTersedia(false);
                kursiRepository.save(kursi);

                Tiket tiket = Tiket.builder()
                                .user(user)
                                .penerbangan(booking.getPenerbangan())
                                .booking(booking)
                                .kursi(kursi).build();
                tiketRepository.save(tiket);

                emailService.kirimTiket(
                                user.getEmail(),
                                booking.getKodeBooking(),
                                booking.getPenumpang().getNama(),
                                booking.getPenerbangan().getMaskapai(),
                                booking.getPenerbangan().getKotaKeberangkatan(),
                                booking.getPenerbangan().getKotaTujuan(),
                                booking.getPenerbangan().getWaktuKeberangkatan().toString(),
                                kursi.getNomorkursi());

                return ResponseEntity.ok(ResponseApi.sukses(
                                "Tiket Berhasil Dibuat Dengan Nomor kursi + " + kursi.getNomorkursi(), null,
                                HttpStatus.OK.value()));
        }

        @GetMapping("/{tiketId}/download-tiket-PDF")
        public ResponseEntity<?> downloadTiket(@PathVariable Long tiketId, Principal principal) {
                try {
                        Tiket tiket = tiketRepository.findById(tiketId).orElseThrow(
                                        () -> new TidakDitemukanException("Data Tiket Anda Tidak Di Temukan"));

                        if (!tiket.getUser().getEmail().equals(principal.getName())) {
                                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(ResponseApi.gagal("Akses Di Tolak, Tiket Bukan Milik Anda", null,
                                                                HttpStatus.FORBIDDEN.value()));
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        Document document = new Document(PageSize.A5.rotate(), 30, 30, 20, 20);
                        PdfWriter.getInstance(document, baos);
                        document.open();

                        Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD, BaseColor.BLUE);
                        Font labelFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
                        Font valueFont = new Font(Font.FontFamily.HELVETICA, 12);

                        Paragraph title = new Paragraph("✈ BOARDING PASS ✈", titleFont);
                        title.setAlignment(Element.ALIGN_CENTER);
                        document.add(title);
                        document.add(new LineSeparator());
                        document.add(new Paragraph());

                        PdfPTable table = new PdfPTable(2);
                        table.setWidthPercentage(100);
                        table.setWidths(new int[] { 1, 2 });

                        table.addCell(new Phrase("Nama Penumpang: ", labelFont));
                        table.addCell(new Phrase(tiket.getBooking().getPenumpang().getNama(), valueFont));

                        table.addCell(new Phrase("Maskapai: ", labelFont));
                        table.addCell(new Phrase(tiket.getPenerbangan().getMaskapai(), valueFont));

                        table.addCell(new Phrase("Waktu Berangkat: ", labelFont));
                        table.addCell(new Phrase(tiket.getPenerbangan().getWaktuKeberangkatan().toString(), valueFont));

                        table.addCell(new Phrase("Waktu Kedatangan: ", labelFont));
                        table.addCell(new Phrase(tiket.getPenerbangan().getWaktuKedatangan().toString(), valueFont));

                        table.addCell(new Phrase("Nomor Kursi: ", labelFont));
                        table.addCell(new Phrase(tiket.getKursi().getNomorkursi(), valueFont));

                        table.addCell(new Phrase("Kode Booking: ", labelFont));
                        table.addCell(new Phrase(tiket.getBooking().getKodeBooking(), valueFont));

                        table.addCell(new Phrase("Status: ", labelFont));
                        table.addCell(new Phrase("SUDAH DIBAYAR", valueFont));

                        document.add(table);

                        document.add(new Paragraph(" "));
                        BarcodeQRCode qrCode = new BarcodeQRCode("Kode Booking: " + tiket.getBooking().getKodeBooking(),
                                        100, 100, null);
                        Image qrImage = qrCode.getImage();
                        qrImage.scaleAbsolute(100, 100);
                        qrImage.setAlignment(Image.ALIGN_RIGHT);
                        document.add(qrImage);

                        document.close();

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=tiket_" + tiket.getId())
                                        .contentType(MediaType.APPLICATION_PDF)
                                        .body(baos.toByteArray());
                } catch (DocumentException error) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ResponseApi.gagal(
                                                        "Kesalahan saat memproses dokumen PDF: " + error.getMessage(),
                                                        null, HttpStatus.INTERNAL_SERVER_ERROR.value()));
                }
        }

        @GetMapping("/melihat-penerbangan-tersedia")
        public ResponseEntity<?> melihatPenerbangan(@RequestParam(required = false) String dari,
                        @RequestParam(required = false) String ke,
                        @RequestParam(required = false) String maskapai,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal) {
                List<PenerbanganResponseDto> response = adminService.ambilPenerbanganTersedia(dari, ke, tanggal, maskapai);
                return ResponseEntity.ok(
                                ResponseApi.sukses("Data Penerbangan Yang Tersedia", response, HttpStatus.OK.value()));
        }

        @GetMapping("/daftar-kota")
        public ResponseEntity<?> daftarKota() {
                java.util.TreeSet<String> kota = new java.util.TreeSet<>();
                kota.addAll(penerbanganRepository.findDistinctKotaKeberangkatan());
                kota.addAll(penerbanganRepository.findDistinctKotaTujuan());
                return ResponseEntity.ok(
                                ResponseApi.sukses("Daftar Kota", List.copyOf(kota), HttpStatus.OK.value()));
        }

        @GetMapping("/daftar-maskapai")
        public ResponseEntity<?> daftarMaskapai() {
                return ResponseEntity.ok(ResponseApi.sukses("Daftar Maskapai",
                                penerbanganRepository.findDistinctMaskapai(), HttpStatus.OK.value()));
        }

        @GetMapping("/melihat-kursi-tersedia")
        public ResponseEntity<?> melihatDataKursi(@RequestParam String kodeBooking){
                List<KursiResponseDto> response = kursiService.ambilDataKursiTersedia(kodeBooking);
                return ResponseEntity.ok(ResponseApi.sukses("Data Berhasil Di Dapat",response, HttpStatus.OK.value()));
        }

        @GetMapping("/melihat-peta-kursi")
        public ResponseEntity<?> melihatPetaKursi(@RequestParam String kodeBooking) {
                Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(
                                () -> new TidakDitemukanException("Kode Booking Tidak Di Temukan"));
                return ResponseEntity.ok(ResponseApi.sukses("Peta Kursi Penerbangan",
                                buatPetaKursi(booking.getPenerbangan().getId()), HttpStatus.OK.value()));
        }

        @GetMapping("/melihat-peta-kursi-penerbangan")
        public ResponseEntity<?> melihatPetaKursiPenerbangan(@RequestParam Long penerbanganId) {
                return ResponseEntity.ok(ResponseApi.sukses("Peta Kursi Penerbangan",
                                buatPetaKursi(penerbanganId), HttpStatus.OK.value()));
        }

        private List<Map<String, Object>> buatPetaKursi(Long penerbanganId) {
                List<Kursi> listKursi = kursiRepository.findByPenerbanganId(penerbanganId);

                listKursi.sort((a, b) -> {
                        int perbandinganBaris = a.getNomorkursi().charAt(0) - b.getNomorkursi().charAt(0);
                        if (perbandinganBaris != 0)
                                return perbandinganBaris;
                        return a.getNomorkursi().substring(1).compareTo(b.getNomorkursi().substring(1));
                });

                return listKursi.stream().map(kursi -> Map.<String, Object>of(
                                "id", kursi.getId(),
                                "nomorKursi", kursi.getNomorkursi(),
                                "tersedia", kursi.isKursiTersedia())).collect(java.util.stream.Collectors.toList());
        }

        @GetMapping("/profile")
        public ResponseEntity<?> lihatProfil(Principal principal) {
                User user = userRepository.findByEmail(principal.getName())
                                .orElseThrow(() -> new TidakDitemukanException("User Tidak Di Temukan"));
                Map<String, Object> data = Map.of(
                                "id", user.getId(),
                                "email", user.getEmail(),
                                "nama", user.getNama() != null ? user.getNama() : "",
                                "role", user.getRole());
                return ResponseEntity.ok(ResponseApi.sukses("Data Profil", data, HttpStatus.OK.value()));
        }

        @PutMapping("/profile-update")
        public ResponseEntity<?> updateProfil(@RequestBody Map<String, String> request, Principal principal) {
                User user = userRepository.findByEmail(principal.getName())
                                .orElseThrow(() -> new TidakDitemukanException("User Tidak Di Temukan"));

                if (request.containsKey("nama") && request.get("nama") != null
                                && !request.get("nama").isBlank()) {
                        user.setNama(request.get("nama"));
                }
                userRepository.save(user);
                Map<String, Object> data = Map.of(
                                "email", user.getEmail(),
                                "nama", user.getNama() != null ? user.getNama() : "");
                return ResponseEntity.ok(ResponseApi.sukses("Profil Berhasil Di Update", data, HttpStatus.OK.value()));
        }

        @PutMapping("/change-password")
        public ResponseEntity<?> gantiPassword(@RequestBody Map<String, String> request, Principal principal) {
                User user = userRepository.findByEmail(principal.getName())
                                .orElseThrow(() -> new TidakDitemukanException("User Tidak Di Temukan"));

                if (!passwordEncoder.matches(request.get("passwordLama"), user.getPassword())) {
                        return ResponseEntity.badRequest().body(ResponseApi.gagal("Password lama salah", null,
                                        HttpStatus.BAD_REQUEST.value()));
                }
                user.setPassword(passwordEncoder.encode(request.get("passwordBaru")));
                userRepository.save(user);
                return ResponseEntity.ok(ResponseApi.sukses("Password Berhasil Diubah", null, HttpStatus.OK.value()));
        }

        @GetMapping("/riwayat-pemesanan")
        public ResponseEntity<?> riwayatPemesanan(Principal principal) {
                User user = userRepository.findByEmail(principal.getName())
                                .orElseThrow(() -> new TidakDitemukanException("User Tidak Di Temukan"));
                List<Booking> bookings = bookingRepository.findByPenumpang_User_Id(user.getId());
                List<Map<String, Object>> result = bookings.stream().map(booking -> {
                        Tiket tiket = booking.getTiker();
                        // LinkedHashMap, bukan Map.of: pesanan tanpa tiket punya nomorKursi/tiketId
                        // bernilai null dan Map.of menolak null.
                        Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("kodeBooking", booking.getKodeBooking());
                        item.put("maskapai", booking.getPenerbangan().getMaskapai());
                        item.put("kotaKeberangkatan", booking.getPenerbangan().getKotaKeberangkatan());
                        item.put("kotaTujuan", booking.getPenerbangan().getKotaTujuan());
                        item.put("waktuKeberangkatan", booking.getPenerbangan().getWaktuKeberangkatan().toString());
                        item.put("waktuKedatangan", booking.getPenerbangan().getWaktuKedatangan().toString());
                        item.put("totalHarga", booking.getTotalHarga());
                        item.put("statusPembayaran", booking.getStatusPembayaran().name());
                        item.put("nomorKursi", tiket != null && tiket.getKursi() != null ? tiket.getKursi().getNomorkursi() : null);
                        item.put("tiketId", tiket != null ? tiket.getId() : null);
                        return item;
                }).collect(java.util.stream.Collectors.toList());
                return ResponseEntity.ok(ResponseApi.sukses("Riwayat Pemesanan", result, HttpStatus.OK.value()));
        }

        @GetMapping("/{kodeBooking}/detail")
        public ResponseEntity<?> detailBooking(@PathVariable String kodeBooking, Principal principal) {
                Booking booking = bookingRepository.findByKodeBooking(kodeBooking).orElseThrow(
                                () -> new TidakDitemukanException("Booking Tidak Di Temukan"));
                if (booking.getPenumpang() == null || !booking.getPenumpang().getUser().getEmail().equals(principal.getName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ResponseApi.gagal("Akses Di Tolak", null, HttpStatus.FORBIDDEN.value()));
                }
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("kodeBooking", booking.getKodeBooking());
                data.put("midtransOrderId", booking.getMidtransOrderId());
                data.put("namaPenumpang", booking.getPenumpang().getNama());
                data.put("noHP", booking.getPenumpang().getNoHP());
                data.put("maskapai", booking.getPenerbangan().getMaskapai());
                data.put("dari", booking.getPenerbangan().getKotaKeberangkatan());
                data.put("ke", booking.getPenerbangan().getKotaTujuan());
                data.put("waktuKeberangkatan", booking.getPenerbangan().getWaktuKeberangkatan().toString());
                data.put("waktuKedatangan", booking.getPenerbangan().getWaktuKedatangan().toString());
                data.put("totalHarga", booking.getTotalHarga());
                data.put("statusPembayaran", booking.getStatusPembayaran().name());
                data.put("batasWaktuPembayaran", booking.getBatasWaktuPembayaran().toString());
                data.put("nomorKursi", booking.getTiker() != null && booking.getTiker().getKursi() != null
                                ? booking.getTiker().getKursi().getNomorkursi() : null);
                data.put("tiketId", booking.getTiker() != null ? booking.getTiker().getId() : null);
                return ResponseEntity.ok(ResponseApi.sukses("Detail Booking", data, HttpStatus.OK.value()));
        }

        @GetMapping("/{kodeBooking}/pembayaran")
        @Operation(summary = "Ambil Token Pembayaran", description = "Melanjutkan pembayaran pesanan yang belum lunas tanpa membuat transaksi baru")
        public ResponseEntity<?> ambilPembayaran(@PathVariable String kodeBooking, Principal principal) {
                Map<String, String> pembayaran = paymentService.ambilTransaksiSnap(kodeBooking, principal.getName());
                return ResponseEntity.ok(ResponseApi.sukses("Data pembayaran pesanan", pembayaran,
                                HttpStatus.OK.value()));
        }

        public record PaymentSyncRequest(String orderId) {}

        @PostMapping("/{kodeBooking}/sync-payment")
        public ResponseEntity<?> syncPayment(@PathVariable String kodeBooking, Principal principal,
                        @RequestBody(required = false) PaymentSyncRequest request) {
                Booking booking = paymentService.syncPayment(kodeBooking, principal.getName(),
                                request == null ? null : request.orderId());
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("kodeBooking", booking.getKodeBooking());
                data.put("statusPembayaran", booking.getStatusPembayaran().name());
                data.put("midtransOrderId", booking.getMidtransOrderId());
                return ResponseEntity.ok(ResponseApi.sukses("Status pembayaran diperiksa", data, HttpStatus.OK.value()));
        }

        @Transactional
        @PostMapping("/{kodeBooking}/batalkan")
        public ResponseEntity<?> batalkanBooking(@PathVariable String kodeBooking, Principal principal) {
                Booking booking = bookingRepository.findForUpdateByKodeBooking(kodeBooking).orElseThrow(
                                () -> new TidakDitemukanException("Booking Tidak Di Temukan"));
                if (booking.getPenumpang() == null || !booking.getPenumpang().getUser().getEmail().equals(principal.getName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(ResponseApi.gagal("Akses Di Tolak", null, HttpStatus.FORBIDDEN.value()));
                }

                if (booking.getStatusPembayaran() == StatusPembayaran.SUDAH_DIBAYAR) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Booking yang sudah dibayar tidak bisa dibatalkan", null,
                                                        HttpStatus.BAD_REQUEST.value()));
                }

                if (booking.getStatusPembayaran() == StatusPembayaran.CANCEL) {
                        return ResponseEntity.badRequest()
                                        .body(ResponseApi.gagal("Booking sudah dibatalkan sebelumnya", null,
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
