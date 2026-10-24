package org.example.config;

import org.example.manager.BookingManager;
import org.example.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationServiceTest {

    @Mock
    BookingManager manager;

    @Mock
    EmailService emailService;

    @Mock
    SmsService smsService;

    @InjectMocks
    AppointmentNotificationService notifications;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        Client client = new Client("Jordan Lee", "jordan@test.com", "555-1000");
        Provider provider = new Provider("Alex", "alex@test.com", "555-0001",
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        Service service = new Service("Consultation", "30 min", 30, 50.0);
        appointment = new Appointment(client, provider, service,
                LocalDateTime.now().plusDays(14).withHour(10).withMinute(0));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
    }

    @Test
    void onAppointmentBooked_alwaysSendsEmailConfirmation() {
        appointment.setReminderChannel(ReminderChannel.EMAIL);

        notifications.onAppointmentBooked(appointment);

        verify(emailService).sendConfirmation(appointment);
        verify(smsService, never()).sendBookingConfirmation(any());
    }

    @Test
    void onAppointmentBooked_smsChannel_sendsSmsConfirmation() {
        appointment.setReminderChannel(ReminderChannel.SMS);
        when(smsService.sendBookingConfirmation(appointment)).thenReturn(true);

        notifications.onAppointmentBooked(appointment);

        verify(emailService).sendConfirmation(appointment);
        verify(smsService).sendBookingConfirmation(appointment);
        verify(manager, never()).markReminderSent(anyString());
    }

    @Test
    void sendScheduledReminders_sendsEmailWithin25Hours() {
        appointment.setReminderChannel(ReminderChannel.EMAIL);
        appointment.setReminderSent(false);
        LocalDateTime start = LocalDateTime.now().plusHours(12);
        appointment = spyAppointmentWithStart(start);

        when(manager.getAllAppointments()).thenReturn(java.util.List.of(appointment));
        when(emailService.sendReminder(appointment)).thenReturn(true);

        notifications.sendScheduledReminders();

        verify(emailService).sendReminder(appointment);
        verify(manager).markReminderSent(appointment.getId());
    }

    @Test
    void sendScheduledReminders_skipsWhenMoreThan25HoursOut() {
        LocalDateTime start = LocalDateTime.now().plusHours(30);
        appointment = spyAppointmentWithStart(start);

        when(manager.getAllAppointments()).thenReturn(java.util.List.of(appointment));

        notifications.sendScheduledReminders();

        verify(emailService, never()).sendReminder(any());
    }

    private Appointment spyAppointmentWithStart(LocalDateTime start) {
        Client client = new Client("Jordan Lee", "jordan@test.com", "555-1000");
        Provider provider = new Provider("Alex", "alex@test.com", "555-0001",
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        Service service = new Service("Consultation", "30 min", 30, 50.0);
        Appointment a = new Appointment(client, provider, service, start);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setReminderChannel(ReminderChannel.EMAIL);
        return a;
    }
}
