package com.projekan.tiket_pesawat.handler;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projekan.tiket_pesawat.dto.ResponseApi;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HandleJwtPenolakanAkses implements AccessDeniedHandler, AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        writeError(response, HttpServletResponse.SC_FORBIDDEN,
                "Akses Ditolak!. kamu tidak punya izin ke endpoint ini");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Silakan login terlebih dahulu untuk mengakses endpoint ini.");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ResponseApi<Object> bodyResponse = ResponseApi.gagal(message, null, status);

        objectMapper.writeValue(response.getOutputStream(), bodyResponse);
    }

}
