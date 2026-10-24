package org.example.config;

import org.example.model.Appointment;
import org.example.model.Client;
import org.example.model.Provider;
import org.example.model.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SmsServiceTest {

    SmsService smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsService();
        ReflectionTestUtils.setField(smsService, "smsEnabled", false);
    }

    @Test
    void normalizePhone_usTenDigit() {
        assertThat(SmsService.normalizePhone("205-746-1694")).isEqualTo("+12057461694");
    }

    @Test
    void normalizePhone_e164Passthrough() {
        assertThat(SmsService.normalizePhone("+12057461694")).isEqualTo("+12057461694");
    }

    @Test
    void normalizePhone_rejectsTooShort() {
        assertThat(SmsService.normalizePhone("12345")).isNull();
    }

    @Test
    void normalizePhone_blank() {
        assertThat(SmsService.normalizePhone("  ")).isNull();
    }

    @Test
    void send_whenDisabled_returnsFalse() {
        assertThat(smsService.send("+12055551234", "hello")).isFalse();
    }

    @Test
    void isConfigured_falseWhenDisabled() {
        assertThat(smsService.isConfigured()).isFalse();
    }

    @Test
    void reminderDestination_prefersAppointmentSnapshot() {
        Client client = new Client("Jordan", "j@test.com", "555-0000");
        Provider provider = new Provider("Alex", "a@test.com", "555-0001",
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0));
        Service service = new Service("Cut", "desc", 30, 10.0);
        Appointment appt = new Appointment(client, provider, service,
                java.time.LocalDateTime.now().plusDays(1));
        appt.setReminderPhone("+15559998888");

        assertThat(SmsService.reminderDestination(appt)).isEqualTo("+15559998888");
    }
}
