package com.projekan.tiket_pesawat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.midtrans.Midtrans;
import com.midtrans.httpclient.SnapApi;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.Penumpang;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
@ResourceLock("midtrans-global-config")
class PaymentServiceTest {
    private static final String CODE = "ASTRA-7001AA8D";
    private static final String ORDER = CODE + "-1788360000000";
    private static final String OWNER = "traveler@example.test";
    private static final String TEST_KEY = "unit-test-only-server-key";

    @Mock
    private BookingRepository repository;
    @Mock
    private MidtransStatusClient statusClient;
    @Mock
    private PaymentStatusUpdater updater;

    private PaymentService service;
    private String previousServerKey;

    @BeforeEach
    void setUp() {
        previousServerKey = Midtrans.serverKey;
        Midtrans.serverKey = TEST_KEY;
        service = new PaymentService(repository, statusClient, updater);
    }

    @AfterEach
    void restoreConfiguration() {
        Midtrans.serverKey = previousServerKey;
    }

    @Test
    void unknownBookingNeverContactsMidtrans() {
        when(repository.findByKodeBooking(CODE)).thenReturn(Optional.empty());

        assertThrows(TidakDitemukanException.class, () -> service.syncPayment(CODE, OWNER, ORDER));

        verifyNoInteractions(statusClient, updater);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "someone-else@example.test" })
    void rejectsMissingOrForeignOwnerBeforeNetworkEvenForPaidBookings(String email) {
        Booking booking = booking();
        booking.setStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
        givenBooking(booking);

        assertStatus(403, () -> service.syncPayment(CODE, email, ORDER));

        verifyNoInteractions(statusClient, updater);
    }

    @Test
    void missingBookingOwnerIsForbiddenBeforeNetwork() {
        Booking booking = booking();
        booking.setPenumpang(null);
        givenBooking(booking);

        assertStatus(403, () -> service.syncPayment(CODE, OWNER, ORDER));

        verifyNoInteractions(statusClient, updater);
    }

    @Test
    void alreadyPaidOwnedBookingDoesNotQueryOrMutateProviderState() {
        Booking booking = booking();
        booking.setStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
        givenBooking(booking);

        assertSame(booking, service.syncPayment(CODE, OWNER, null));

        verifyNoInteractions(statusClient, updater);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void legacyBookingWithoutAnOrderIdRequiresIdentification(String requestedOrder) {
        givenBooking(booking());

        assertStatus(428, () -> service.syncPayment(CODE, OWNER, requestedOrder));

        verifyNoInteractions(statusClient, updater);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ASTRA-AAAAAAAA-1788360000000", "ASTRA-7001AA8D", "ASTRA-7001AA8D-123",
            "ASTRA-7001AA8D-17883600000000", "ASTRA-7001AA8D-1788360000000/status",
            "https://example.test/status", "ASTRA-7001AA8D-1788360000000?x=1"
    })
    void malformedOrDifferentBookingOrderIsRejectedBeforeNetwork(String requestedOrder) {
        givenBooking(booking());

        assertStatus(400, () -> service.syncPayment(CODE, OWNER, requestedOrder));

        verifyNoInteractions(statusClient, updater);
    }

    @Test
    void cannotReplaceAnAlreadyBoundOrderId() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        givenBooking(booking);

        assertStatus(409, () -> service.syncPayment(CODE, OWNER, CODE + "-1788360000001"));

        verifyNoInteractions(statusClient, updater);
        assertEquals(ORDER, booking.getMidtransOrderId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "  " })
    void usesPersistedOrderWhenCallerOmitsIt(String requestedOrder) {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        givenBooking(booking);
        JSONObject current = new JSONObject().put("transaction_status", "pending");
        when(statusClient.check(ORDER)).thenReturn(current);
        when(updater.apply(CODE, ORDER, current, OWNER)).thenReturn(booking);

        assertSame(booking, service.syncPayment(CODE, OWNER, requestedOrder));

        verify(updater).apply(CODE, ORDER, current, OWNER);
    }

    @Test
    void legacyOrderHintIsNotBoundBeforeAuthoritativeUpdaterValidation() {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject current = new JSONObject().put("transaction_status", "settlement");
        when(statusClient.check(ORDER)).thenReturn(current);
        when(updater.apply(CODE, ORDER, current, OWNER)).thenReturn(booking);

        assertSame(booking, service.syncPayment(CODE, OWNER, "  " + ORDER + "  "));

        assertNull(booking.getMidtransOrderId());
        verify(updater).apply(CODE, ORDER, current, OWNER);
    }

    @Test
    void snapNotYetChargedDoesNotBindOrCancelLegacyBooking() {
        Booking booking = booking();
        givenBooking(booking);
        when(statusClient.check(ORDER)).thenReturn(new JSONObject().put("status_code", "404"));

        assertSame(booking, service.syncPayment(CODE, OWNER, ORDER));

        assertNull(booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        verifyNoInteractions(updater);
    }

    @Test
    void providerFailureLeavesBookingUnchanged() {
        Booking booking = booking();
        givenBooking(booking);
        when(statusClient.check(ORDER)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertStatus(503, () -> service.syncPayment(CODE, OWNER, ORDER));

        assertNull(booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        verifyNoInteractions(updater);
    }

    @Test
    void rejectedProviderResponseDoesNotBindLegacyOrder() {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject current = new JSONObject();
        when(statusClient.check(ORDER)).thenReturn(current);
        when(updater.apply(CODE, ORDER, current, OWNER))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertStatus(502, () -> service.syncPayment(CODE, OWNER, ORDER));

        assertNull(booking.getMidtransOrderId());
    }

    @Test
    void missingOrInvalidWebhookSignatureNeverQueriesProvider() {
        JSONObject notification = new JSONObject().put("order_id", ORDER)
                .put("status_code", "200").put("gross_amount", "750000.00");

        assertThrows(IllegalStateException.class, () -> service.prosesNotifikasi(notification));
        notification.put("signature_key", "invalid-signature");
        assertThrows(IllegalStateException.class, () -> service.prosesNotifikasi(notification));

        verifyNoInteractions(repository, statusClient, updater);
    }

    @Test
    void signedMalformedWebhookOrderStillCannotReachProvider() throws Exception {
        JSONObject notification = signedNotification("ASTRA-7001AA8D-invalid");

        assertThrows(IllegalArgumentException.class, () -> service.prosesNotifikasi(notification));

        verifyNoInteractions(repository, statusClient, updater);
    }

    @Test
    void webhookUsesCurrentProviderStateNotItsClaimedTransactionStatus() throws Exception {
        JSONObject notification = signedNotification(ORDER).put("transaction_status", "settlement");
        JSONObject current = new JSONObject().put("transaction_status", "pending");
        when(statusClient.check(ORDER)).thenReturn(current);

        service.prosesNotifikasi(notification);

        verify(updater).apply(CODE, ORDER, current, null);
        verifyNoInteractions(repository);
    }

    @Test
    void signedWebhookWithProvider404DoesNotUpdateAnything() throws Exception {
        when(statusClient.check(ORDER)).thenReturn(new JSONObject().put("status_code", "404"));
        JSONObject notification = signedNotification(ORDER);
        assertStatus(503, () -> service.prosesNotifikasi(notification));

        verifyNoInteractions(repository, updater);
    }

    @Test
    void fullOrderIdIsSavedBeforeProviderCreationAndReturnedForRecovery() {
        Booking booking = booking();
        booking.getPenumpang().setNama("Test Traveler");
        booking.getPenumpang().setNoHP("08000000000");
        booking.setPenerbangan(Penerbangan.builder().id(1L).kotaKeberangkatan("Jakarta").kotaTujuan("Bali").build());
        givenBooking(booking);
        try (var snap = mockStatic(SnapApi.class)) {
            snap.when(() -> SnapApi.createTransaction(any())).thenAnswer(invocation -> {
                verify(repository).saveAndFlush(booking);
                assertNotNull(booking.getMidtransOrderId());
                return new JSONObject().put("token", "fake-test-token")
                        .put("redirect_url", "https://app.sandbox.midtrans.com/snap/test");
            });
            var result = service.buatTransaksiSnap(CODE);
            assertEquals(booking.getMidtransOrderId(), result.get("orderId"));
            assertTrue(result.get("orderId").matches(CODE + "-\\d{13}"));
        }
    }

    @Test
    void fractionalPriceNeverCreatesAnUnverifiableCharge() {
        Booking booking = booking();
        booking.setTotalHarga(new BigDecimal("750000.50"));
        givenBooking(booking);
        try (var snap = mockStatic(SnapApi.class)) {
            assertThrows(IllegalStateException.class, () -> service.buatTransaksiSnap(CODE));
            snap.verifyNoInteractions();
            verify(repository, never()).saveAndFlush(any());
        }
    }

    @Test
    void existingSnapTokenIsReusedInsteadOfCreatingASecondOrder() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        booking.setSnapToken("token-tersimpan");
        booking.setSnapRedirectUrl("https://app.sandbox.midtrans.com/snap/v1/lama");
        givenBooking(booking);

        try (var snap = mockStatic(SnapApi.class)) {
            var result = service.buatTransaksiSnap(CODE);

            snap.verifyNoInteractions();
            verify(repository, never()).saveAndFlush(any());
            assertEquals(ORDER, result.get("orderId"));
            assertEquals("token-tersimpan", result.get("snapToken"));
        }
    }

    @Test
    void failedSnapCallReleasesTheOrderIdSoTheBookingIsNotStranded() {
        Booking booking = booking();
        givenBooking(booking);

        try (var snap = mockStatic(SnapApi.class)) {
            snap.when(() -> SnapApi.createTransaction(any()))
                    .thenThrow(new IllegalStateException("midtrans menolak"));

            assertThrows(IllegalStateException.class, () -> service.buatTransaksiSnap(CODE));
        }

        // Tanpa pelepasan ini pesanan tersangkut: Order ID terisi tetapi tidak ada
        // transaksi nyata, sehingga percobaan berikutnya selalu ditolak.
        assertNull(booking.getMidtransOrderId());
        assertNull(booking.getSnapToken());
    }

    @Test
    void snapTokenIsPersistedSoOtherTabsCanContinueThePayment() {
        Booking booking = booking();
        booking.getPenumpang().setNama("Test Traveler");
        booking.getPenumpang().setNoHP("08000000000");
        booking.setPenerbangan(Penerbangan.builder().id(1L).kotaKeberangkatan("Jakarta").kotaTujuan("Bali").build());
        givenBooking(booking);

        try (var snap = mockStatic(SnapApi.class)) {
            snap.when(() -> SnapApi.createTransaction(any()))
                    .thenReturn(new JSONObject().put("token", "token-baru")
                            .put("redirect_url", "https://app.sandbox.midtrans.com/snap/v1/baru"));

            service.buatTransaksiSnap(CODE);
        }

        assertEquals("token-baru", booking.getSnapToken());
        assertEquals("https://app.sandbox.midtrans.com/snap/v1/baru", booking.getSnapRedirectUrl());
    }

    @Test
    void storedTokenIsReturnedToTheOwnerOfTheBooking() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        booking.setSnapToken("token-tersimpan");
        booking.setSnapRedirectUrl("https://app.sandbox.midtrans.com/snap/v1/lama");
        givenBooking(booking);

        var result = service.ambilTransaksiSnap(CODE, OWNER);

        assertEquals("token-tersimpan", result.get("snapToken"));
        assertEquals(ORDER, result.get("orderId"));
    }

    @Test
    void storedTokenIsNeverHandedToSomeoneElse() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        booking.setSnapToken("token-tersimpan");
        givenBooking(booking);

        assertThrows(RuntimeException.class, () -> service.ambilTransaksiSnap(CODE, "orang-lain@example.test"));
    }

    @Test
    void legacyBookingWithoutStoredTokenAsksForManualRecoveryInsteadOfPayingTwice() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        givenBooking(booking);

        assertStatus(HttpStatus.PRECONDITION_REQUIRED.value(),
                () -> service.ambilTransaksiSnap(CODE, OWNER));
    }

    private void givenBooking(Booking booking) {
        when(repository.findByKodeBooking(CODE)).thenReturn(Optional.of(booking));
    }

    private static Booking booking() {
        return Booking.builder().id(10L).kodeBooking(CODE).totalHarga(new BigDecimal("750000.00"))
                .statusPembayaran(StatusPembayaran.BELUM_DIBAYAR)
                .penumpang(Penumpang.builder().user(User.builder().email(OWNER).build()).build()).build();
    }

    private static JSONObject signedNotification(String order) throws Exception {
        String amount = "750000.00";
        String statusCode = "200";
        byte[] signature = MessageDigest.getInstance("SHA-512")
                .digest((order + statusCode + amount + TEST_KEY).getBytes(StandardCharsets.UTF_8));
        return new JSONObject().put("order_id", order).put("gross_amount", amount)
                .put("status_code", statusCode).put("signature_key", HexFormat.of().formatHex(signature));
    }

    private static void assertStatus(int expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
