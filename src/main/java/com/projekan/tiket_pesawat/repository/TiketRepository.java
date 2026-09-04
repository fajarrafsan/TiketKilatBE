package com.projekan.tiket_pesawat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.Tiket;

@Repository
public interface TiketRepository extends JpaRepository<Tiket, Long>{
    boolean existsByBooking(Booking booking);
}
