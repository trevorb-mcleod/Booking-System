package org.example.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdStartupChecksTest {

    @Test
    void rejectsWeakPin() {
        MockEnvironment env = baseEnv();
        env.setProperty("booking.admin.pin", "1234");
        ProdStartupChecks checks = new ProdStartupChecks(env);

        assertThatThrownBy(() -> checks.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOKING_ADMIN_PIN");
    }

    @Test
    void rejectsLocalhostBaseUrl() {
        MockEnvironment env = baseEnv();
        env.setProperty("booking.base-url", "http://localhost:8080");
        ProdStartupChecks checks = new ProdStartupChecks(env);

        assertThatThrownBy(() -> checks.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOKING_BASE_URL");
    }

    @Test
    void acceptsMinimalProdConfig() {
        MockEnvironment env = baseEnv();
        ProdStartupChecks checks = new ProdStartupChecks(env);

        assertThatCode(() -> checks.run(null)).doesNotThrowAnyException();
    }

    @Test
    void emailEnabled_requiresSmtpSecrets() {
        MockEnvironment env = baseEnv();
        env.setProperty("booking.email.enabled", "true");
        env.setProperty("booking.reminders.from", "noreply@example.com");
        ProdStartupChecks checks = new ProdStartupChecks(env);

        assertThatThrownBy(() -> checks.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL");
    }

    @Test
    void emailEnabled_acceptsBrevoApiKeyWithoutSmtp() {
        MockEnvironment env = baseEnv();
        env.setProperty("booking.email.enabled", "true");
        env.setProperty("booking.reminders.from", "noreply@example.com");
        env.setProperty("booking.brevo.api-key", "xkeysib-test");
        ProdStartupChecks checks = new ProdStartupChecks(env);

        assertThatCode(() -> checks.run(null)).doesNotThrowAnyException();
    }

    private static MockEnvironment baseEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("booking.admin.pin", "prod-pin-not-in-repo");
        env.setProperty("booking.base-url", "https://booksmart-demo.onrender.com");
        env.setProperty("booking.email.enabled", "false");
        env.setProperty("booking.sms.enabled", "false");
        return env;
    }
}
