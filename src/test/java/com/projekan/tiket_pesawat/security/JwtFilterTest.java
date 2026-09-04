package com.projekan.tiket_pesawat.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projekan.tiket_pesawat.filters.JwtFilter;
import com.projekan.tiket_pesawat.utils.JwtUtil;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {
    private static final String EMAIL = "traveler@example.test";
    private static final String PATH = "/user/melihat-penerbangan-tersedia";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private JwtFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtFilter(jwtUtil, userDetailsService, objectMapper);
        request = new MockHttpServletRequest("GET", PATH);
        request.setServletPath(PATH);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "Basic credentials", "Bearer" })
    void missingBearerTokenReturnsJson401WithoutCallingDownstream(String authorization) throws Exception {
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertUnauthorizedJson();
        verifyNoInteractions(jwtUtil, userDetailsService, filterChain);
    }

    @Test
    void invalidTokenReturnsJson401WithoutThrowingOrCallingDownstream() throws Exception {
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtUtil.extractEmail("invalid-token")).thenThrow(new JwtException("Invalid signature"));

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertUnauthorizedJson();
        verifyNoInteractions(userDetailsService, filterChain);
    }

    @Test
    void expiredTokenReturnsJson401WithoutThrowingOrCallingDownstream() throws Exception {
        request.addHeader("Authorization", "Bearer expired-token");
        when(jwtUtil.extractEmail("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "Expired token"));

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertUnauthorizedJson();
        verifyNoInteractions(userDetailsService, filterChain);
    }

    @Test
    void tokenForUnknownUserReturnsJson401WithoutCallingDownstream() throws Exception {
        request.addHeader("Authorization", "Bearer unknown-user-token");
        when(jwtUtil.extractEmail("unknown-user-token")).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("User was removed"));

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertUnauthorizedJson();
        verifyNoInteractions(filterChain);
    }

    @Test
    void failedTokenValidationReturnsJson401WithoutCallingDownstream() throws Exception {
        request.addHeader("Authorization", "Bearer rejected-token");
        when(jwtUtil.extractEmail("rejected-token")).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userWithAuthority("USER"));
        when(jwtUtil.validateToken("rejected-token", EMAIL)).thenReturn(false);

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));

        assertUnauthorizedJson();
        verifyNoInteractions(filterChain);
    }

    @ParameterizedTest
    @CsvSource({ "USER, USER", "ROLE_USER, USER", "ROLE_ADMIN, USER", "ADMIN, ADMIN", "USER, ADMIN" })
    void validTokenUsesCurrentDatabaseAuthoritiesIncludingLegacyRoleClaims(
            String tokenRole, String databaseAuthority) throws Exception {
        JwtUtil signingJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(signingJwtUtil, "kunciRahasia",
                "jwt-filter-regression-test-secret-not-for-production");
        ReflectionTestUtils.setField(signingJwtUtil, "waktuKardaluarsa", 300_000L);
        String token = signingJwtUtil.generateToken(EMAIL, tokenRole);
        assertEquals(tokenRole, signingJwtUtil.extractRole(token));
        UserDetails userDetails = userWithAuthority(databaseAuthority);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        request.addHeader("Authorization", "Bearer " + token);
        JwtFilter signedTokenFilter = new JwtFilter(signingJwtUtil, userDetailsService, objectMapper);

        assertDoesNotThrow(() -> signedTokenFilter.doFilter(request, response, filterChain));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(userDetails, authentication.getPrincipal());
        assertEquals(List.of(databaseAuthority), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList());
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    private UserDetails userWithAuthority(String authority) {
        return User.withUsername(EMAIL).password("unused-test-password").authorities(authority).build();
    }

    private void assertUnauthorizedJson() throws Exception {
        assertEquals(401, response.getStatus());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertFalse(body.path("sukses").asBoolean(true));
        assertEquals(401, body.path("statusKode").asInt());
        assertFalse(body.path("pesanNya").asText().isBlank());
        assertDoesNotThrow(() -> LocalDateTime.parse(body.path("stempelWaktu").asText()));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
