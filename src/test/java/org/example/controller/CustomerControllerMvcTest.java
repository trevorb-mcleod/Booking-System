package org.example.controller;

import org.example.config.AppointmentNotificationService;
import org.example.config.EmailService;
import org.example.config.GlobalModelAttributes;
import org.example.config.ReminderOptions;
import org.example.manager.BookingManager;
import org.example.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import(GlobalModelAttributes.class)
class CustomerControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookingManager manager;

    @MockitoBean
    EmailService emailService;

    @MockitoBean
    ReminderOptions reminderOptions;

    @MockitoBean
    AppointmentNotificationService notifications;

    private Client client;
    private Provider provider;
    private org.example.model.Service service;
    private Appointment appointment;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        bookingDate = LocalDate.now().plusDays(21);
        client = new Client("Jordan Lee", "jordan@test.com", "555-1000");
        provider = new Provider("Alex", "alex@test.com", "555-0001",
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        service = new org.example.model.Service("Consultation", "30 min", 30, 50.0);
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));
        appointment = new Appointment(client, provider, service, start);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        when(manager.getAllProviders()).thenReturn(List.of(provider));
        when(manager.getAllServices()).thenReturn(List.of(service));
        when(reminderOptions.remindersEnabled()).thenReturn(true);
        when(reminderOptions.smsChoiceAvailable()).thenReturn(true);
    }

    @Test
    void getBook_returnsBookingView() throws Exception {
        mockMvc.perform(get("/book"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/book"))
                .andExpect(model().attribute("smsRemindersAvailable", true));
    }

    @Test
    void postBook_success_redirectsToConfirmationAndNotifies() throws Exception {
        when(manager.findClientByEmail("jordan@test.com")).thenReturn(Optional.empty());
        when(manager.addClient(anyString(), anyString(), anyString())).thenReturn(client);
        when(manager.bookAppointment(eq(client.getId()), eq(provider.getId()), eq(service.getId()),
                any(LocalDateTime.class), anyString(), eq(ReminderChannel.EMAIL), isNull()))
                .thenReturn(Optional.of(appointment));
        when(manager.findAppointmentById(appointment.getId())).thenReturn(Optional.of(appointment));

        mockMvc.perform(post("/book")
                        .param("customerName", "Jordan Lee")
                        .param("customerEmail", "jordan@test.com")
                        .param("customerPhone", "555-1000")
                        .param("providerId", provider.getId())
                        .param("serviceId", service.getId())
                        .param("date", bookingDate.toString())
                        .param("selectedTime", "10:00 AM")
                        .param("reminderChannel", "EMAIL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/confirmation"))
                .andExpect(flash().attribute("apptId", appointment.getId()));

        verify(notifications).onAppointmentBooked(appointment);
    }

    @Test
    void postBook_smsWithoutPhone_redirectsWithError() throws Exception {
        mockMvc.perform(post("/book")
                        .param("customerName", "Jordan Lee")
                        .param("customerEmail", "jordan@test.com")
                        .param("customerPhone", " ")
                        .param("providerId", provider.getId())
                        .param("serviceId", service.getId())
                        .param("date", bookingDate.toString())
                        .param("selectedTime", "10:00 AM")
                        .param("reminderChannel", "SMS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/book"))
                .andExpect(flash().attribute("errorMsg",
                        "Please enter a mobile number for SMS reminders."));

        verify(manager, never()).bookAppointment(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any());
    }

    @Test
    void postBook_existingClient_reusesRecordWithoutRecreating() throws Exception {
        when(manager.findClientByEmail("jordan@test.com")).thenReturn(Optional.of(client));
        when(manager.bookAppointment(eq(client.getId()), eq(provider.getId()), eq(service.getId()),
                any(LocalDateTime.class), anyString(), eq(ReminderChannel.EMAIL), isNull()))
                .thenReturn(Optional.of(appointment));
        when(manager.findAppointmentById(appointment.getId())).thenReturn(Optional.of(appointment));

        mockMvc.perform(post("/book")
                        .param("customerName", "Attacker Name")
                        .param("customerEmail", "jordan@test.com")
                        .param("customerPhone", "555-9999")
                        .param("providerId", provider.getId())
                        .param("serviceId", service.getId())
                        .param("date", bookingDate.toString())
                        .param("selectedTime", "10:00 AM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/confirmation"));

        verify(manager, never()).addClient(anyString(), anyString(), anyString());
    }

    @Test
    void postBook_unavailableSlot_redirectsWithError() throws Exception {
        when(manager.findClientByEmail(anyString())).thenReturn(Optional.empty());
        when(manager.addClient(anyString(), anyString(), anyString())).thenReturn(client);
        when(manager.bookAppointment(anyString(), anyString(), anyString(),
                any(LocalDateTime.class), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/book")
                        .param("customerName", "Jordan Lee")
                        .param("customerEmail", "jordan@test.com")
                        .param("customerPhone", "555-1000")
                        .param("providerId", provider.getId())
                        .param("serviceId", service.getId())
                        .param("date", bookingDate.toString())
                        .param("selectedTime", "10:00 AM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/book"))
                .andExpect(flash().attribute("errorMsg",
                        "That time slot is no longer available. Please choose another."));

        verify(notifications, never()).onAppointmentBooked(any());
    }

    @Test
    void getCancel_validToken_showsAppointment() throws Exception {
        when(manager.findAppointmentByCancelToken(appointment.getCancelToken()))
                .thenReturn(Optional.of(appointment));

        mockMvc.perform(get("/cancel/" + appointment.getCancelToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/cancel"))
                .andExpect(model().attribute("isConfirmed", true))
                .andExpect(model().attribute("isCancelled", false));
    }

    @Test
    void getCancel_unknownToken_marksInvalid() throws Exception {
        when(manager.findAppointmentByCancelToken("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/cancel/bad-token"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("invalid", true));
    }

    @Test
    void postCancel_confirmedAppointment_cancelsAndEmails() throws Exception {
        String token = appointment.getCancelToken();
        when(manager.findAppointmentByCancelToken(token)).thenReturn(Optional.of(appointment));

        mockMvc.perform(post("/cancel/" + token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cancel/" + token))
                .andExpect(flash().attribute("justCancelled", true));

        verify(manager).cancelAppointment(appointment.getId());
        verify(emailService).sendCancellation(appointment);
    }

    @Test
    void postCancel_unknownToken_redirectsHome() throws Exception {
        when(manager.findAppointmentByCancelToken("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/cancel/missing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(manager, never()).cancelAppointment(anyString());
    }
}
