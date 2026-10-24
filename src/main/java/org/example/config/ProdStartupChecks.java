package org.example.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fails fast on {@code prod} if required public config is missing or unsafe defaults are used.
 * Does not log secret values.
 */
@Component
@Profile("prod")
public class ProdStartupChecks implements ApplicationRunner {

    private static final Set<String> WEAK_PINS = Set.of("", "1234", "CHANGE_ME");

    private final Environment env;

    public ProdStartupChecks(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        String pin = env.getProperty("booking.admin.pin", "");
        if (WEAK_PINS.contains(pin)) {
            throw new IllegalStateException(
                    "Production requires BOOKING_ADMIN_PIN (env) set to a strong, non-default value.");
        }

        String baseUrl = env.getProperty("booking.base-url", "");
        if (baseUrl.isBlank() || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "Production requires BOOKING_BASE_URL to your public https URL (used in email cancel links).");
        }

        if (Boolean.parseBoolean(env.getProperty("booking.email.enabled", "false"))) {
            requireNonBlank("SPRING_MAIL_USERNAME", env.getProperty("spring.mail.username"));
            requireNonBlank("SPRING_MAIL_PASSWORD", env.getProperty("spring.mail.password"));
            requireNonBlank("BOOKING_REMINDERS_FROM", env.getProperty("booking.reminders.from"));
        }

        if (Boolean.parseBoolean(env.getProperty("booking.sms.enabled", "false"))) {
            requireNonBlank("TWILIO_ACCOUNT_SID", env.getProperty("twilio.account-sid"));
            requireNonBlank("TWILIO_FROM_NUMBER", env.getProperty("twilio.from-number"));
            boolean apiKey = !isBlank(env.getProperty("twilio.api-key-sid"))
                    && !isBlank(env.getProperty("twilio.api-key-secret"));
            boolean authToken = !isBlank(env.getProperty("twilio.auth-token"));
            if (!apiKey && !authToken) {
                throw new IllegalStateException(
                        "SMS enabled in production: set TWILIO_AUTH_TOKEN or TWILIO_API_KEY_SID + TWILIO_API_KEY_SECRET.");
            }
        }
    }

    private static void requireNonBlank(String envName, String value) {
        if (isBlank(value)) {
            throw new IllegalStateException("Production misconfiguration: " + envName + " must be set.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
