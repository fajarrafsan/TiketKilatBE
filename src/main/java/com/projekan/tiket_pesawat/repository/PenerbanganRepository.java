package com.projekan.tiket_pesawat.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import jakarta.persistence.criteria.Predicate;

@Repository
public interface PenerbanganRepository extends JpaRepository<Penerbangan, Long>, JpaSpecificationExecutor<Penerbangan> {
        default List<Penerbangan> findFiltered(String dariKota, String keKota,
                        LocalDate tanggal, String maskapai) {
                return findAll((root, query, cb) -> {
                        List<Predicate> predicates = new ArrayList<>();
                        predicates.add(cb.equal(root.get("ketersediaanPenerbangan"),
                                        KetersediaanPenerbangan.TERSEDIA));

                        // Omit empty filters so PostgreSQL never has to infer a null parameter's type.
                        if (dariKota != null && !dariKota.isBlank()) {
                                predicates.add(cb.like(cb.lower(root.<String>get("kotaKeberangkatan")),
                                                "%" + dariKota.trim().toLowerCase(Locale.ROOT) + "%"));
                        }
                        if (keKota != null && !keKota.isBlank()) {
                                predicates.add(cb.like(cb.lower(root.<String>get("kotaTujuan")),
                                                "%" + keKota.trim().toLowerCase(Locale.ROOT) + "%"));
                        }
                        if (maskapai != null && !maskapai.isBlank()) {
                                predicates.add(cb.like(cb.lower(root.<String>get("maskapai")),
                                                "%" + maskapai.trim().toLowerCase(Locale.ROOT) + "%"));
                        }
                        if (tanggal != null) {
                                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("waktuKeberangkatan"),
                                                tanggal.atStartOfDay()));
                                predicates.add(cb.lessThan(root.<LocalDateTime>get("waktuKeberangkatan"),
                                                tanggal.plusDays(1).atStartOfDay()));
                        }
                        return cb.and(predicates.toArray(Predicate[]::new));
                });
        }

        @Query("SELECT DISTINCT p.kotaKeberangkatan FROM Penerbangan p ORDER BY p.kotaKeberangkatan")
        List<String> findDistinctKotaKeberangkatan();

        @Query("SELECT DISTINCT p.kotaTujuan FROM Penerbangan p ORDER BY p.kotaTujuan")
        List<String> findDistinctKotaTujuan();

        @Query("SELECT DISTINCT p.maskapai FROM Penerbangan p ORDER BY p.maskapai")
        List<String> findDistinctMaskapai();

        long countByKetersediaanPenerbangan(com.projekan.tiket_pesawat.models.KetersediaanPenerbangan ketersediaanPenerbangan);
        long countByStatusPenerbangan(com.projekan.tiket_pesawat.models.StatusPenerbangan statusPenerbangan);
}
