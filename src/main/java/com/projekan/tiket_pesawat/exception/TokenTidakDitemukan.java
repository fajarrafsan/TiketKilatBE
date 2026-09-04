package com.projekan.tiket_pesawat.exception;

public class TokenTidakDitemukan extends RuntimeException {
    public TokenTidakDitemukan(String pesan){
        super(pesan);
    }
}
