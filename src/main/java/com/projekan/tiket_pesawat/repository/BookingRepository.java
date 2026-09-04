package com.projekan.tiket_pesawat.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.StatusPembayaran;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByStatusPembayaranAndBatasWaktuPembayaranBefore(StatusPembayaran statusPembayaran, LocalDateTime batasWaktu);
    Optional<Booking> findByKodeBooking(String kodeBooking);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.kodeBooking = :kode")
    Optional<Booking> findForUpdateByKodeBooking(@Param("kode") String kodeBooking);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Booking b SET b.statusPembayaran = :canceled "
            + "WHERE b.id = :id AND b.statusPembayaran = :unpaid "
            + "AND b.batasWaktuPembayaran < :now")
    int expireIfStillUnpaid(@Param("id") Long id, @Param("now") LocalDateTime now,
            @Param("unpaid") StatusPembayaran unpaid, @Param("canceled") StatusPembayaran canceled);
    List<Booking> findByPenumpang_User_Id(Long userId);
    boolean existsByPenumpang_User_Id(Long userId);
    long countByStatusPembayaran(StatusPembayaran statusPembayaran);

    @Query("SELECT b FROM Booking b "
            + "WHERE (:maskapai IS NULL OR LOWER(b.penerbangan.maskapai) LIKE LOWER(CONCAT('%', :maskapai, '%'))) "
            + "AND (:status IS NULL OR b.statusPembayaran = :status) "
            + "AND (:tanggal IS NULL OR DATE(b.waktuBooking) = :tanggal)")
    List<Booking> findFiltered(@Param("maskapai") String maskapai,
            @Param("status") StatusPembayaran status,
            @Param("tanggal") LocalDate tanggal);
}
