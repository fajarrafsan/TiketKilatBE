package com.projekan.tiket_pesawat.services;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface CustomUserDetailsService extends UserDetailsService{
    UserDetails loadUserByUsername(String email);
}
