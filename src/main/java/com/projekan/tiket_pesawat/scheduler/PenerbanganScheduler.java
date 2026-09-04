package com.projekan.tiket_pesawat.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.StatusPenerbangan;
import com.projekan.tiket_pesawat.repository.PenerbanganRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PenerbanganScheduler {
    
    private final PenerbanganRepository penerbanganRepository;

    @Scheduled(fixedRate = 60000)
    public void updateStatusPenerbangan() {
        List<Penerbangan> listPenerbangan = penerbanganRepository.findAll();
        LocalDateTime waktuSekarang = LocalDateTime.now();

        for (Penerbangan penerbangan : listPenerbangan) {
            LocalDateTime waktuBerangkat = penerbangan.getWaktuKeberangkatan();
            LocalDateTime waktuTiba = penerbangan.getWaktuKedatangan();

            if (waktuSekarang.isAfter(waktuBerangkat) && waktuSekarang.isBefore(waktuTiba)) {
                penerbangan.setStatusPenerbangan(StatusPenerbangan.DEPARTED);
                penerbangan.setKetersediaanPenerbangan(KetersediaanPenerbangan.TIDAK_TERSEDIA);
            } else if(waktuSekarang.isAfter(waktuTiba)){
                penerbangan.setStatusPenerbangan(StatusPenerbangan.ARRIVED);
            }

            penerbanganRepository.save(penerbangan);
        }
    }
}
