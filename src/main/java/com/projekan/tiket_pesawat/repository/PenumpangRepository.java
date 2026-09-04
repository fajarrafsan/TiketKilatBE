package com.projekan.tiket_pesawat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projekan.tiket_pesawat.models.Penumpang;

@Repository
public interface PenumpangRepository extends JpaRepository<Penumpang,Long>{
    
}
