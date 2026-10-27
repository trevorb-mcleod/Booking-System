package org.example.config;

import org.example.model.Appointment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;

/**
 * Sends transactional emails (confirmation + cancellation).
 *
 * Safe to inject everywhere — if booking.email.enabled=false or SMTP is not
 * configured, all send calls are silently skipped.
 *
 * To enable:
 *   1. Set booking.email.enabled=true
 *   2. On Render and other SMTP-blocked hosts: set BREVO_API_KEY (HTTPS API)
 *   3. Locally: spring.mail.* SMTP credentials, or BREVO_API_KEY
 */
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    /** Null when spring-boot-starter-mail is not configured. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private BrevoApiClient brevoApiClient;

    @Value("${booking.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${booking.reminders.from:no-reply@yourbusiness.com}")
    private String fromAddress;

    @Value("${booking.business-name:BookSmart}")
    private String businessName;

    @Value("${booking.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @PostConstruct
    public void logStatus() {
        if (!emailEnabled) {
            System.out.println("[EmailService] Disabled (booking.email.enabled=false).");
            return;
        }
        if (brevoApiClient.isConfigured()) {
            System.out.printf("[EmailService] Ready — sending from %s via Brevo HTTPS API.%n", fromAddress);
            return;
        }
        if (mailSender == null) {
            System.err.println("[EmailService] WARNING — enabled but no BREVO_API_KEY and JavaMailSender is not available.");
            return;
        }
        boolean credsMissing = smtpUsername.isBlank()
                || smtpUsername.equals("your-email@gmail.com");
        if (credsMissing) {
            System.err.println("[EmailService] WARNING — set BREVO_API_KEY (hosted) or spring.mail.* SMTP credentials (local).");
        } else {
            System.out.printf("[EmailService] Ready — sending from %s via SMTP %s.%n", fromAddress, smtpUsername);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void sendConfirmation(Appointment a) {
        send(a.getClient().getEmail(),
             "Booking Confirmed – " + businessName,
             confirmationBody(a));
    }

    public void sendCancellation(Appointment a) {
        send(a.getClient().getEmail(),
             "Appointment Cancelled – " + businessName,
             cancellationBody(a));
    }

    /** ~24-hour reminder; returns true if the message was sent. */
    public boolean sendReminder(Appointment a) {
        return send(a.getClient().getEmail(),
                "Reminder: Your appointment tomorrow at " + businessName,
                reminderBody(a));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private boolean send(String to, String subject, String body) {
        if (!emailEnabled || to == null || to.isBlank()) return false;

        if (brevoApiClient.isConfigured()) {
            return brevoApiClient.send(fromAddress, businessName, to, to, subject, body);
        }
        if (mailSender == null) return false;

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            System.out.printf("[EmailService] Sent \"%s\" to %s%n", subject, to);
            return true;
        } catch (Exception ex) {
            System.err.printf("[EmailService] Failed to send to %s: %s%n", to, ex.getMessage());
            return false;
        }
    }

    private String reminderBody(Appointment a) {
        String cancelLink = (a.getCancelToken() != null)
                ? baseUrl + "/cancel/" + a.getCancelToken() : "";
        return String.format(
                "Hi %s,%n%n" +
                "This is a friendly reminder about your upcoming appointment at %s.%n%n" +
                "  Confirmation #: %s%n" +
                "  Provider:       %s%n" +
                "  Service:        %s%n" +
                "  Date:           %s%n" +
                "  Time:           %s – %s%n%n" +
                (cancelLink.isEmpty() ? "" :
                "Need to cancel? Use the link below:%n" +
                cancelLink + "%n%n") +
                "See you soon!%n" +
                "The %s Team",
                a.getClient().getName(),
                businessName,
                a.getId(),
                a.getProvider().getName(),
                a.getService().getName(),
                a.getStartTime().format(DATE_FMT),
                a.getStartTime().format(TIME_FMT),
                a.getEndTime().format(TIME_FMT),
                businessName);
    }

    private String confirmationBody(Appointment a) {
        String cancelLink = (a.getCancelToken() != null)
                ? baseUrl + "/cancel/" + a.getCancelToken() : "";
        return String.format(
                "Hi %s,%n%n" +
                "Your appointment has been confirmed!%n%n" +
                "  Confirmation #: %s%n" +
                "  Provider:       %s%n" +
                "  Service:        %s%n" +
                "  Date:           %s%n" +
                "  Time:           %s – %s%n%n" +
                "We look forward to seeing you!%n%n" +
                (cancelLink.isEmpty() ? "" :
                "Need to cancel? Use the link below (valid until your appointment time):%n" +
                cancelLink + "%n%n") +
                "The %s Team",
                a.getClient().getName(),
                a.getId(),
                a.getProvider().getName(),
                a.getService().getName(),
                a.getStartTime().format(DATE_FMT),
                a.getStartTime().format(TIME_FMT),
                a.getEndTime().format(TIME_FMT),
                businessName);
    }

    private String cancellationBody(Appointment a) {
        return String.format(
                "Hi %s,%n%n" +
                "Your appointment has been cancelled.%n%n" +
                "  Confirmation #: %s%n" +
                "  Provider:       %s%n" +
                "  Service:        %s%n" +
                "  Date:           %s%n" +
                "  Time:           %s%n%n" +
                "If you'd like to rebook, please visit our booking page or contact us directly.%n%n" +
                "The %s Team",
                a.getClient().getName(),
                a.getId(),
                a.getProvider().getName(),
                a.getService().getName(),
                a.getStartTime().format(DATE_FMT),
                a.getStartTime().format(TIME_FMT),
                businessName);
    }
}

