package org.example.config;

import org.example.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    BrevoApiClient brevoApiClient;

    EmailService emailService;

    Appointment appointment;

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailService, "brevoApiClient", brevoApiClient);
        when(brevoApiClient.isConfigured()).thenReturn(false);
        ReflectionTestUtils.setField(emailService, "emailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@booksmart.test");
        ReflectionTestUtils.setField(emailService, "businessName", "BookSmart");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(emailService, "smtpUsername", "smtp@test.com");

        Client client = new Client("Jordan Lee", "jordan@test.com", "555-1000");
        Provider provider = new Provider("Alex", "alex@test.com", "555-0001",
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        Service service = new Service("Consultation", "30 min", 30, 50.0);
        appointment = new Appointment(client, provider, service,
                LocalDateTime.of(2026, 10, 23, 14, 30));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
    }

    @Test
    void sendConfirmation_whenDisabled_doesNotCallMailSender() {
        ReflectionTestUtils.setField(emailService, "emailEnabled", false);

        emailService.sendConfirmation(appointment);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendConfirmation_whenEnabled_sendsWithCancelLink() {
        emailService.sendConfirmation(appointment);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();

        assertThat(msg.getTo()).containsExactly("jordan@test.com");
        assertThat(msg.getSubject()).contains("Booking Confirmed");
        assertThat(msg.getText()).contains("Your appointment has been confirmed!");
        assertThat(msg.getText()).contains("/cancel/" + appointment.getCancelToken());
    }

    @Test
    void sendReminder_returnsTrueWhenSent() {
        assertThat(emailService.sendReminder(appointment)).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendReminder_returnsFalseWhenMailFails() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(emailService.sendReminder(appointment)).isFalse();
    }
}
