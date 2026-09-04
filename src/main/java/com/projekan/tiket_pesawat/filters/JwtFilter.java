package com.projekan.tiket_pesawat.filters;

import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projekan.tiket_pesawat.dto.ResponseApi;
import com.projekan.tiket_pesawat.utils.JwtUtil;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtutil;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || path.startsWith("/auth/")
            || path.equals("/payment/notification")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs");
    }

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            handleUnauthorized(response, "Token tidak ditemukan. Silakan login terlebih dahulu.");
            return;
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtutil.extractEmail(token);
            if (email == null || email.isBlank()) {
                handleUnauthorized(response, "Token tidak valid. Silakan login ulang.");
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtutil.validateToken(token, userDetails.getUsername())) {
                handleUnauthorized(response, "Token tidak valid. Silakan login ulang.");
                return;
            }

            // Use the account's current permissions, including for older refreshed tokens.
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (ExpiredJwtException error) {
            handleUnauthorized(response, "Token sudah expired. Silakan login ulang.");
            return;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException error) {
            handleUnauthorized(response, "Token tidak valid. Silakan periksa kembali token Anda.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void handleUnauthorized(HttpServletResponse response, String pesan) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ResponseApi<Object> bodyResponse = ResponseApi.gagal(pesan, null,
                HttpServletResponse.SC_UNAUTHORIZED);

        objectMapper.writeValue(response.getOutputStream(), bodyResponse);
    }
}
