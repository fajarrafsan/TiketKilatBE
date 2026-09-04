package com.projekan.tiket_pesawat.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.projekan.tiket_pesawat.models.Booking;
import com.projekan.tiket_pesawat.models.Penumpang;
import com.projekan.tiket_pesawat.models.StatusPembayaran;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.BookingRepository;
import com.projekan.tiket_pesawat.services.PaymentService;

@ExtendWith(MockitoExtension.class)
class PaymentSyncControllerTest {
    private static final String CODE = "ASTRA-7001AA8D";
    private static final String ORDER = CODE + "-1788360000000";
    private static final String OWNER = "traveler@example.test";
    @Mock private PaymentService payments;
    @Mock private BookingRepository bookings;
    @InjectMocks private UserController users;
    @InjectMocks private AdminController admins;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        // Standalone controllers only: no application, scheduler, database, or provider starts.
        mvc = MockMvcBuilders.standaloneSetup(users, new PaymentController(payments))
                .setControllerAdvice(new ErrorController()).build();
    }

    @Test
    void lookupUsesAuthenticatedPrincipalAndReturnsOnlyPaymentFields() throws Exception {
        when(payments.syncPayment(CODE, OWNER, ORDER)).thenReturn(paidBooking());
        mvc.perform(post("/user/" + CODE + "/sync-payment").principal(() -> OWNER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + ORDER + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sukses").value(true))
                .andExpect(jsonPath("$.data.statusPembayaran").value("SUDAH_DIBAYAR"))
                .andExpect(jsonPath("$.data.midtransOrderId").value(ORDER))
                .andExpect(jsonPath("$.data.penumpang").doesNotExist());
        verify(payments).syncPayment(CODE, OWNER, ORDER);
    }

    @Test
    void noRequestBodyUsesPersistedOrder() throws Exception {
        when(payments.syncPayment(CODE, OWNER, null)).thenReturn(paidBooking());
        mvc.perform(post("/user/" + CODE + "/sync-payment").principal(() -> OWNER))
                .andExpect(status().isOk());
        verify(payments).syncPayment(CODE, OWNER, null);
    }

    @Test
    void legacyMissingIdErrorRemainsActionable() throws Exception {
        when(payments.syncPayment(CODE, OWNER, null)).thenThrow(new ResponseStatusException(
                HttpStatus.PRECONDITION_REQUIRED, "Order ID pesanan lama belum tersedia."));
        mvc.perform(post("/user/" + CODE + "/sync-payment").principal(() -> OWNER))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.pesanNya").value("Order ID pesanan lama belum tersedia."));
    }

    @Test
    void temporaryWebhookFailureIsRetryableNotAcknowledgedAsSuccess() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Status belum tersedia."))
                .when(payments).prosesNotifikasi(any());
        mvc.perform(post("/payment/notification").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.sukses").value(false));
    }

    @Test
    void invalidNotificationIsRejected() throws Exception {
        doThrow(new IllegalStateException("Signature Midtrans tidak valid"))
                .when(payments).prosesNotifikasi(any());
        mvc.perform(post("/payment/notification").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void neitherUserNorAdminCanOverwriteALockedPaidBookingWithCancel() {
        Booking booking = paidBooking();
        when(bookings.findForUpdateByKodeBooking(CODE)).thenReturn(Optional.of(booking));
        assertEquals(400, users.batalkanBooking(CODE, () -> OWNER).getStatusCode().value());
        assertEquals(400, admins.cancelBookingByAdmin(CODE).getStatusCode().value());
        assertEquals(StatusPembayaran.SUDAH_DIBAYAR, booking.getStatusPembayaran());
        verify(bookings, never()).save(any());
    }

    private Booking paidBooking() {
        return Booking.builder().kodeBooking(CODE).midtransOrderId(ORDER)
                .statusPembayaran(StatusPembayaran.SUDAH_DIBAYAR)
                .penumpang(Penumpang.builder().user(User.builder().email(OWNER).build()).build()).build();
    }
}
