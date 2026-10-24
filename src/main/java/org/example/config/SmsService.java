package org.example.config;

import org.example.model.Appointment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Sends SMS via Twilio REST API.
 *
 * Enable with booking.sms.enabled=true and Twilio credentials in application.properties.
 */
@Service
public class SmsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${booking.business-name:BookSmart}")
    private String businessName;

    @Value("${booking.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${booking.sms.enabled:false}")
    private boolean smsEnabled;

    /**
     * Twilio trial accounts reject custom SMS bodies (error 572006).
     * When true, sends predefined template {@code sms_appointment_reminders} instead.
     */
    @Value("${booking.sms.trial-mode:false}")
    private boolean trialMode;

    /** Account SID from Twilio Console (starts with AC). Used in the REST URL. */
    @Value("${twilio.account-sid:}")
    private String accountSid;

    /**
     * Primary Auth Token from the Twilio Console (used with Account SID for login).
     * If you use an API key instead, leave this blank and set api-key-sid + api-key-secret.
     */
    @Value("${twilio.auth-token:}")
    private String authToken;

    /** Optional API Key SID (starts with SK) — pair with api-key-secret. */
    @Value("${twilio.api-key-sid:}")
    private String apiKeySid;

    /** Secret shown once when you create a Twilio API key. */
    @Value("${twilio.api-key-secret:}")
    private String apiKeySecret;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    @PostConstruct
    public void logStatus() {
        if (!smsEnabled) {
            System.out.println("[SmsService] Disabled (booking.sms.enabled=false).");
            return;
        }
        if (!hasValidCredentials() || fromNumber.isBlank()) {
            System.err.println("[SmsService] WARNING — enabled but Twilio credentials or twilio.from-number is missing.");
        } else {
            System.out.printf("[SmsService] Ready — sending from %s.%n", fromNumber);
            System.out.println("[SmsService] twilio.from-number must be a Twilio SMS number you bought (Console → Phone Numbers), not your personal cell.");
            System.out.println("[SmsService] Trial accounts must verify recipient numbers (Console → Phone Numbers → Verified Caller IDs).");
            if (trialMode) {
                System.out.println("[SmsService] Trial template mode ON — using Twilio template sms_appointment_reminders.");
            }
        }
    }

    public boolean isConfigured() {
        return smsEnabled && hasValidCredentials() && !fromNumber.isBlank();
    }

    private boolean hasValidCredentials() {
        if (accountSid.isBlank() || !accountSid.startsWith("AC")) return false;
        boolean apiKey = !apiKeySid.isBlank() && !apiKeySecret.isBlank();
        boolean primary = !authToken.isBlank();
        return apiKey || primary;
    }

    private String basicAuthUser() {
        return !apiKeySid.isBlank() ? apiKeySid : accountSid;
    }

    private String basicAuthPassword() {
        return !apiKeySecret.isBlank() ? apiKeySecret : authToken;
    }

    /** Immediate booking confirmation when the customer chose SMS reminders. */
    public boolean sendBookingConfirmation(Appointment a) {
        return send(reminderDestination(a), placeholderBody(a), "booking confirmation");
    }

    public boolean sendReminder(Appointment a) {
        return send(reminderDestination(a), placeholderBody(a), "reminder");
    }

    static String reminderDestination(Appointment a) {
        String snap = a.getReminderPhone();
        if (snap != null && !snap.isBlank()) return snap;
        return a.getClient().getPhone();
    }

    /** Used only when {@code booking.sms.trial-mode=false} (upgraded Twilio account). */
    private String placeholderBody(Appointment a) {
        return String.format(
                "%s: %s with %s on %s at %s. Conf #%s.",
                businessName,
                a.getService().getName(),
                a.getProvider().getName(),
                a.getStartTime().format(DATE_FMT),
                a.getStartTime().format(TIME_FMT),
                a.getId());
    }

    public boolean send(String toPhone, String body) {
        return send(toPhone, body, "message");
    }

    private boolean send(String toPhone, String customBody, String kind) {
        if (!isConfigured()) {
            System.err.println("[SmsService] Send skipped — SMS not fully configured.");
            return false;
        }
        if (toPhone == null || toPhone.isBlank()) {
            System.err.println("[SmsService] Send skipped — client has no phone number on file.");
            return false;
        }

        String to = normalizePhone(toPhone);
        if (to == null) {
            System.err.printf("[SmsService] Invalid phone number: %s%n", toPhone);
            return false;
        }

        String body = resolveBody(customBody);
        if (trialMode) {
            System.out.printf("[SmsService] Trial template send (%s) — Body=%s%n", kind, body);
            System.out.println("[SmsService] Twilio trial texts use Twilio's sample date/time, not your booking. "
                    + "Email confirmation shows the real appointment; upgrade Twilio + trial-mode=false for accurate SMS.");
        }

        try {
            String form = "To=" + encode(to)
                    + "&From=" + encode(fromNumber)
                    + "&Body=" + encode(body);
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
            String basic = Base64.getEncoder()
                    .encodeToString((basicAuthUser() + ":" + basicAuthPassword())
                            .getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.printf("[SmsService] Sent SMS to %s%n", to);
                return true;
            }
            System.err.printf("[SmsService] Twilio error %d: %s%n", response.statusCode(), response.body());
            return false;
        } catch (Exception ex) {
            System.err.printf("[SmsService] Failed to send to %s: %s%n", toPhone, ex.getMessage());
            return false;
        }
    }

    private String resolveBody(String customBody) {
        if (trialMode) {
            return "sms_appointment_reminders";
        }
        return customBody;
    }

    /** Best-effort US/international formatting to E.164. */
    static String normalizePhone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (raw.trim().startsWith("+")) return "+" + digits;
        if (digits.length() == 10) return "+1" + digits;
        if (digits.length() == 11 && digits.startsWith("1")) return "+" + digits;
        if (digits.length() >= 10) return "+" + digits;
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
