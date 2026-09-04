package com.projekan.tiket_pesawat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.midtrans.Midtrans;

@ExtendWith(MockitoExtension.class)
@ResourceLock("midtrans-global-config")
class MidtransStatusClientTest {
    private static final String ORDER = "ASTRA-7001AA8D-1788360000000";
    private static final String TEST_KEY = "unit-test-only-server-key";

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> response;

    private MidtransStatusClient client;
    private String previousServerKey;
    private boolean previousProduction;

    @BeforeEach
    void setUp() {
        previousServerKey = Midtrans.serverKey;
        previousProduction = Midtrans.isProduction;
        Midtrans.serverKey = TEST_KEY;
        Midtrans.isProduction = false;
        client = new MidtransStatusClient(httpClient);
    }

    @AfterEach
    void restoreConfiguration() {
        Midtrans.serverKey = previousServerKey;
        Midtrans.isProduction = previousProduction;
        // The interruption test must not leave the JUnit worker interrupted.
        Thread.interrupted();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void missingServerKeyFailsBeforeNetwork(String key) {
        Midtrans.serverKey = key;

        assertStatus(503, () -> client.check(ORDER));

        verifyNoInteractions(httpClient, response);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void sendsOnlyAuthenticatedGetToConfiguredEnvironmentWithTimeout(boolean production) throws Exception {
        Midtrans.isProduction = production;
        respond(200, new JSONObject().put("order_id", ORDER).put("transaction_status", "settlement").toString());

        JSONObject result = client.check(ORDER);

        assertEquals("settlement", result.getString("transaction_status"));
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        String host = production ? "api.midtrans.com" : "api.sandbox.midtrans.com";
        assertEquals("https://" + host + "/v2/" + ORDER + "/status", request.uri().toString());
        assertEquals("GET", request.method());
        assertEquals(Duration.ofSeconds(7), request.timeout().orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("Basic " + Base64.getEncoder().encodeToString((TEST_KEY + ":").getBytes(StandardCharsets.UTF_8)),
                request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(request.bodyPublisher().isEmpty());
    }

    @Test
    void orderCharactersAreEncodedAndCannotReplaceProviderOrigin() throws Exception {
        respond(200, "{}");

        client.check("https://example.test/order?#value");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("api.sandbox.midtrans.com", request.uri().getHost());
        assertEquals("/v2/https%3A%2F%2Fexample.test%2Forder%3F%23value/status", request.uri().getRawPath());
        assertEquals(null, request.uri().getRawQuery());
        assertEquals(null, request.uri().getRawFragment());
    }

    @Test
    void http404BecomesAnUnchangedStatusSignalWithoutParsingResponseBody() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(404);

        JSONObject result = client.check(ORDER);

        assertEquals("404", result.getString("status_code"));
        verify(response, org.mockito.Mockito.never()).body();
    }

    @ParameterizedTest
    @ValueSource(ints = { 301, 302, 400, 401, 403, 429, 500, 503 })
    void unsuccessfulHttpStatusReturnsSanitizedGatewayError(int statusCode) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(statusCode);

        ResponseStatusException error = assertStatus(502, () -> client.check(ORDER));

        assertFalse(error.getReason().contains(TEST_KEY));
        verify(response, org.mockito.Mockito.never()).body();
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-json", "<html>internal proxy details</html>", "[]", "" })
    void malformedSuccessResponseIsRejectedWithoutLeakingPayload(String body) throws Exception {
        respond(200, body);

        ResponseStatusException error = assertStatus(502, () -> client.check(ORDER));

        assertEquals("Respons status Midtrans tidak valid.", error.getReason());
    }

    @Test
    void networkFailureIsUnavailableAndDoesNotLeakUnderlyingDetails() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("internal upstream information " + TEST_KEY));

        ResponseStatusException error = assertStatus(503, () -> client.check(ORDER));

        assertFalse(error.getReason().contains(TEST_KEY));
        assertTrue(error.getReason().contains("Status pembayaran tidak diubah"));
    }

    @Test
    void requestTimeoutIsUnavailable() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("test timeout"));

        assertStatus(503, () -> client.check(ORDER));
    }

    @Test
    void interruptionIsPreservedAndReportedAsUnavailable() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("test interruption"));

        assertStatus(503, () -> client.check(ORDER));

        assertTrue(Thread.currentThread().isInterrupted());
    }

    private void respond(int statusCode, String body) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
    }

    private static ResponseStatusException assertStatus(int expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
        return error;
    }
}
