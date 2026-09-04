package com.projekan.tiket_pesawat.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.projekan.tiket_pesawat.utils.JwtUtil;

import io.jsonwebtoken.ExpiredJwtException;

class JwtUtilTest {
    private static final String EMAIL = "traveler@example.test";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "kunciRahasia",
                "jwt-util-regression-test-secret-not-for-production");
        ReflectionTestUtils.setField(jwtUtil, "waktuKardaluarsa", 300_000L);
    }

    @Test
    void preservesExpiredJwtExceptionForUnauthorizedHandling() {
        ReflectionTestUtils.setField(jwtUtil, "waktuKardaluarsa", -60_000L);
        String token = jwtUtil.generateToken(EMAIL, "USER");

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.extractEmail(token));
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(token, EMAIL));
    }

    @Test
    void validTokenRoundTripsTheRawAuthority() {
        String token = jwtUtil.generateToken(EMAIL, "USER");

        assertEquals(EMAIL, jwtUtil.extractEmail(token));
        assertEquals("USER", jwtUtil.extractRole(token));
        assertTrue(jwtUtil.validateToken(token, EMAIL));
    }
}
