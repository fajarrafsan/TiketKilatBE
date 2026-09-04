package com.projekan.tiket_pesawat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.midtrans.Midtrans;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class MidtransConfig {

    @Value("${midtrans.server-key:}")
    private String serverKey;

    @Value("${midtrans.client-key:}")
    private String clientKey;

    @Value("${midtrans.is-production:false}")
    private boolean isProduction;

    @PostConstruct
    public void init() {
        if (serverKey == null || serverKey.isBlank()) {
            log.warn("MIDTRANS_SERVER_KEY belum di-set. Fitur pembayaran Midtrans tidak aktif.");
            return;
        }

        // Kunci Sandbox berawalan SB-, kunci Production tidak. Kunci Production yang
        // dipakai dengan is-production=false akan menghantam API Sandbox dan ditolak
        // 401, sehingga pemesanan gagal di tengah jalan tanpa sebab yang jelas.
        String salahPasangan = cariKunciSalahLingkungan();
        if (salahPasangan != null) {
            // Sama seperti server key kosong: pembayaran dinonaktifkan tetapi aplikasi
            // tetap jalan, supaya fitur lain tidak ikut mati karena salah konfigurasi.
            log.error(salahPasangan);
            log.error("Midtrans TIDAK dikonfigurasi. Perbaiki kunci lalu jalankan ulang backend.");
            return;
        }

        Midtrans.serverKey = serverKey;
        Midtrans.clientKey = clientKey;
        Midtrans.isProduction = isProduction;
        log.info("Midtrans dikonfigurasi. Mode production: {}", isProduction);
    }

    private String cariKunciSalahLingkungan() {
        String pesan = cekPasanganKunci("MIDTRANS_SERVER_KEY", serverKey);
        return pesan != null ? pesan : cekPasanganKunci("MIDTRANS_CLIENT_KEY", clientKey);
    }

    private String cekPasanganKunci(String nama, String kunci) {
        if (kunci == null || kunci.isBlank()) return null;

        boolean kunciSandbox = kunci.startsWith("SB-");
        if (kunciSandbox == !isProduction) return null;

        return String.format(
                "Konfigurasi Midtrans tidak konsisten: %s adalah kunci %s sedangkan MIDTRANS_IS_PRODUCTION=%s. "
                        + "Gunakan kunci SB-Mid-* dengan MIDTRANS_IS_PRODUCTION=false untuk Sandbox, "
                        + "atau kunci Mid-* dengan MIDTRANS_IS_PRODUCTION=true untuk transaksi uang sungguhan. "
                        + "Client Key di frontend (NEXT_PUBLIC_MIDTRANS_CLIENT_KEY) harus dari lingkungan yang sama.",
                nama, kunciSandbox ? "Sandbox" : "Production", isProduction);
    }
}
