package com.projekan.tiket_pesawat.exception;

public class TidakDitemukanException extends RuntimeException{
    public TidakDitemukanException(String pesan){
        super(pesan);
    }
}
