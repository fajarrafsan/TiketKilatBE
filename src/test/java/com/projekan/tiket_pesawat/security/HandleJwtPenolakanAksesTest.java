package com.projekan.tiket_pesawat.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projekan.tiket_pesawat.handler.HandleJwtPenolakanAkses;

class HandleJwtPenolakanAksesTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final HandleJwtPenolakanAkses handler = new HandleJwtPenolakanAkses(objectMapper);

    @Test
    void accessDeniedReturnsJson403WithSerializableTimestamp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/dashboard/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> handler.handle(request, response, new AccessDeniedException("Wrong role")));

        assertErrorJson(response, 403);
    }

    @Test
    void unauthenticatedEntryPointReturnsJson401WithSerializableTimestamp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> handler.commence(request, response,
                new InsufficientAuthenticationException("Login required")));

        assertErrorJson(response, 401);
    }

    private void assertErrorJson(MockHttpServletResponse response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.getStatus());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertFalse(body.path("sukses").asBoolean(true));
        assertEquals(expectedStatus, body.path("statusKode").asInt());
        assertFalse(body.path("pesanNya").asText().isBlank());
        assertDoesNotThrow(() -> LocalDateTime.parse(body.path("stempelWaktu").asText()));
    }
}
