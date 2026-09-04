package com.projekan.tiket_pesawat.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import com.projekan.tiket_pesawat.models.Kursi;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.repository.KursiRepository;
import com.projekan.tiket_pesawat.repository.PenerbanganRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KetersediaanPenerbanganScheduler {

    private final PenerbanganRepository penerbanganRepository;
    private final KursiRepository kursiRepository;
    
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void updateKetersediaanPenerbangan() {
        List<Penerbangan> listPenerbangan = penerbanganRepository.findAll();

        for (Penerbangan penerbangan : listPenerbangan) {
            List<Kursi> listKursi = kursiRepository.findByPenerbanganId(penerbangan.getId());

            boolean semuaSudahPenuh = listKursi.stream()
                    .noneMatch(kursi -> kursi.isKursiTersedia());

            if (semuaSudahPenuh && penerbangan.getKetersediaanPenerbangan() != KetersediaanPenerbangan.TIDAK_TERSEDIA) {
                penerbangan.setKetersediaanPenerbangan(KetersediaanPenerbangan.TIDAK_TERSEDIA);
                penerbanganRepository.save(penerbangan);
                System.out.println("Update Penerbangan ID: "+  penerbangan.getId() + " Jadi TIDAK TERSEDIA");
            } else if (!semuaSudahPenuh && penerbangan.getKetersediaanPenerbangan() != KetersediaanPenerbangan.TERSEDIA
                    && penerbangan.getStatusPenerbangan() != null
                    && (penerbangan.getStatusPenerbangan().name().equals("ON_TIME"))) {
                penerbangan.setKetersediaanPenerbangan(KetersediaanPenerbangan.TERSEDIA);
                penerbanganRepository.save(penerbangan);
                System.out.println("Update Penerbangan ID: "+  penerbangan.getId() + " Jadi TERSEDIA");
            }
        }

    }
}
