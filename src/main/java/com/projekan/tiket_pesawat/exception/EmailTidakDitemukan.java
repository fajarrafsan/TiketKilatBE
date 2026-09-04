package com.projekan.tiket_pesawat.exception;

public class EmailTidakDitemukan extends RuntimeException {
    public EmailTidakDitemukan(String pesan){
        super(pesan);
    }
}
