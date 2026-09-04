package com.projekan.tiket_pesawat.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.Penumpang;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.BookingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ExtendWith(MockitoExtension.class)
class PaymentStatusUpdaterTest {
    private static final String CODE = "ASTRA-7001AA8D";
    private static final String ORDER = CODE + "-1788360000000";
    private static final String OWNER = "traveler@example.test";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 9, 2, 12, 0);

    @Mock
    private BookingRepository repository;
    @Mock
    private EntityManager entityManager;
    private PaymentStatusUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new PaymentStatusUpdater(repository, entityManager);
    }

    @Test
    void missingBookingCannotBeUpdated() {
        when(repository.findForUpdateByKodeBooking(CODE)).thenReturn(Optional.empty());

        assertThrows(TidakDitemukanException.class, () -> updater.apply(CODE, ORDER, status("settlement"), OWNER));

        verify(repository, never()).save(any());
    }

    @Test
    void ownerIsRecheckedOnLockedRecordBeforeApplyingStatus() {
        Booking booking = booking();
        givenBooking(booking);

        assertStatus(403, () -> updater.apply(CODE, ORDER, status("settlement"), "other@example.test"));

        assertUnchangedLegacyBooking(booking);
    }

    @Test
    void concurrentOrderBindingCannotBeOverwritten() {
        Booking booking = booking();
        booking.setMidtransOrderId(CODE + "-1788360000001");
        givenBooking(booking);

        assertStatus(409, () -> updater.apply(CODE, ORDER, status("settlement"), OWNER));

        assertEquals(CODE + "-1788360000001", booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        verify(repository, never()).save(any());
    }

    @Test
    void orderForAnotherBookingIsRejectedAgainUnderLock() {
        Booking booking = booking();
        givenBooking(booking);

        assertStatus(400, () -> updater.apply(CODE, "ASTRA-AAAAAAAA-1788360000000", status("settlement"), OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @Test
    void providerResponseMustMatchExactRequestedOrder() {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject response = status("settlement").put("order_id", CODE + "-1788360000001");

        assertStatus(502, () -> updater.apply(CODE, ORDER, response, OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @ParameterizedTest
    @ValueSource(strings = { "749999.99", "750001", "-750000", "0", "NaN", "not-a-number" })
    void malformedOrMismatchedAmountNeverBindsOrPays(String amount) {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject response = status("settlement").put("gross_amount", amount);

        assertStatus(502, () -> updater.apply(CODE, ORDER, response, OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @Test
    void missingAmountNeverBindsOrPays() {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject response = status("settlement");
        response.remove("gross_amount");

        assertStatus(502, () -> updater.apply(CODE, ORDER, response, OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @Test
    void missingBookingAmountFailsClosed() {
        Booking booking = booking();
        booking.setTotalHarga(null);
        givenBooking(booking);

        assertStatus(502, () -> updater.apply(CODE, ORDER, status("settlement"), OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @ParameterizedTest
    @ValueSource(strings = { "USD", "EUR", "", "IDR " })
    void explicitlyWrongCurrencyNeverBindsOrPays(String currency) {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject response = status("settlement").put("currency", currency);

        assertStatus(502, () -> updater.apply(CODE, ORDER, response, OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @Test
    void missingTransactionStatusNeverBindsOrPays() {
        Booking booking = booking();
        givenBooking(booking);
        JSONObject response = status("settlement");
        response.remove("transaction_status");

        assertStatus(502, () -> updater.apply(CODE, ORDER, response, OWNER));

        assertUnchangedLegacyBooking(booking);
    }

    @ParameterizedTest
    @MethodSource("successfulResponses")
    void verifiedSuccessBindsLegacyOrderAndSetsPaid(JSONObject response) {
        Booking booking = booking();
        givenBooking(booking);

        assertSame(booking, updater.apply(CODE, ORDER, response, OWNER));

        assertEquals(ORDER, booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        assertNotNull(booking.getWaktuPembayaran());
        verify(repository).save(booking);
    }

    static Stream<Arguments> successfulResponses() {
        JSONObject noFraud = status("settlement");
        noFraud.remove("fraud_status");
        JSONObject legacyCurrency = status("settlement");
        legacyCurrency.remove("currency");
        return Stream.of(
                Arguments.of(status("settlement")),
                Arguments.of(status("SETTLEMENT")),
                Arguments.of(noFraud),
                Arguments.of(legacyCurrency),
                Arguments.of(status("settlement").put("gross_amount", "750000.000")),
                Arguments.of(status("capture").put("payment_type", "credit_card")));
    }

    @ParameterizedTest
    @MethodSource("notSuccessfulResponses")
    void unacceptedCaptureFraudOrResultCodeDoesNotMarkPaid(JSONObject response) {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        givenBooking(booking);

        updater.apply(CODE, ORDER, response, OWNER);

        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        assertNull(booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    static Stream<Arguments> notSuccessfulResponses() {
        JSONObject captureWithoutFraud = status("capture").put("payment_type", "credit_card");
        captureWithoutFraud.remove("fraud_status");
        return Stream.of(
                Arguments.of(status("settlement").put("fraud_status", "challenge")),
                Arguments.of(status("settlement").put("fraud_status", "deny")),
                Arguments.of(status("settlement").put("status_code", "201")),
                Arguments.of(status("settlement").put("status_code", "500")),
                Arguments.of(status("capture").put("payment_type", "credit_card").put("fraud_status", "challenge")),
                Arguments.of(status("capture").put("payment_type", "credit_card").put("fraud_status", "deny")),
                Arguments.of(captureWithoutFraud),
                Arguments.of(status("capture").put("payment_type", "gopay")),
                Arguments.of(status("authorize")),
                Arguments.of(status("unknown")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "pending", "cancel", "deny", "expire", "refund", "partial_refund", "settlement" })
    void paidBookingNeverRegressesOrGetsANewPaymentTimestamp(String providerStatus) {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        booking.setStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
        booking.setWaktuPembayaran(PAID_AT);
        givenBooking(booking);

        updater.apply(CODE, ORDER, status(providerStatus), OWNER);

        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        assertEquals(PAID_AT, booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    @Test
    void verifiedPaymentCanRecoverALocallyCanceledBooking() {
        Booking booking = booking();
        booking.setStatusPembayaran(StatusPembayaran.CANCEL);
        givenBooking(booking);

        updater.apply(CODE, ORDER, status("settlement"), OWNER);

        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        assertNotNull(booking.getWaktuPembayaran());
        verify(repository).save(booking);
    }

    @Test
    void pendingCannotResurrectCanceledBooking() {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        booking.setStatusPembayaran(StatusPembayaran.CANCEL);
        givenBooking(booking);

        updater.apply(CODE, ORDER, status("pending"), OWNER);

        assertEquals(StatusPembayaran.CANCEL, booking.getStatusPembayaran());
        assertNull(booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "cancel", "deny", "expire" })
    void failedSnapAttemptDoesNotCancelRetryableBooking(String providerStatus) {
        Booking booking = booking();
        booking.setMidtransOrderId(ORDER);
        givenBooking(booking);

        updater.apply(CODE, ORDER, status(providerStatus), OWNER);

        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        assertNull(booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    @Test
    void refreshUnderLockPreservesPaymentCommittedDuringProviderRequest() {
        Booking booking = booking();
        givenBooking(booking);
        doAnswer(invocation -> {
            booking.setMidtransOrderId(ORDER);
            booking.setStatusPembayaran(StatusPembayaran.SUDAH_DIBAYAR);
            booking.setWaktuPembayaran(PAID_AT);
            return null;
        }).when(entityManager).refresh(booking, LockModeType.PESSIMISTIC_WRITE);

        updater.apply(CODE, ORDER, status("pending"), OWNER);

        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        assertEquals(PAID_AT, booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    @Test
    void verifiedPendingMayBindLegacyOrderWithoutClaimingPayment() {
        Booking booking = booking();
        givenBooking(booking);

        updater.apply(CODE, ORDER, status("pending").put("status_code", "201"), OWNER);

        assertEquals(ORDER, booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        assertNull(booking.getWaktuPembayaran());
        verify(repository).save(booking);
    }

    @Test
    void verifiedWebhookMayApplyWithoutBrowserPrincipal() {
        Booking booking = booking();
        givenBooking(booking);

        updater.apply(CODE, ORDER, status("settlement"), null);

        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        verify(repository).save(booking);
    }

    private void givenBooking(Booking booking) {
        when(repository.findForUpdateByKodeBooking(CODE)).thenReturn(Optional.of(booking));
    }

    private void assertUnchangedLegacyBooking(Booking booking) {
        assertNull(booking.getMidtransOrderId());
        assertEquals(StatusPembayaran.BELUM_DIBAYAR, booking.getStatusPembayaran());
        assertNull(booking.getWaktuPembayaran());
        verify(repository, never()).save(any());
    }

    private static Booking booking() {
        return Booking.builder().id(10L).kodeBooking(CODE).totalHarga(new BigDecimal("750000.00"))
                .statusPembayaran(StatusPembayaran.BELUM_DIBAYAR)
                .penumpang(Penumpang.builder().user(User.builder().email(OWNER).build()).build()).build();
    }

    private static JSONObject status(String status) {
        return new JSONObject().put("order_id", ORDER).put("gross_amount", "750000.00")
                .put("currency", "IDR").put("status_code", "200").put("payment_type", "bank_transfer")
                .put("transaction_status", status).put("fraud_status", "accept");
    }

    private static void assertStatus(int expected, Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
