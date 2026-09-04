package com.projekan.tiket_pesawat.exception;

public class StatusTidakValidException  extends RuntimeException{
    public StatusTidakValidException(String pesan) {
        super(pesan);
    }
}
