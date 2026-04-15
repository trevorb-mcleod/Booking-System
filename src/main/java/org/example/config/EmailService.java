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
 *   1. Set booking.email.enabled=true in application.properties
 *   2. Fill in spring.mail.* SMTP credentials
 */
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    /** Null when spring-boot-starter-mail is not configured. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

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
        if (mailSender == null) {
            System.err.println("[EmailService] WARNING — enabled but JavaMailSender is not available. Check spring.mail.* configuration.");
            return;
        }
        boolean credsMissing = smtpUsername.isBlank()
                || smtpUsername.equals("your-email@gmail.com");
        if (credsMissing) {
            System.err.println("[EmailService] WARNING — booking.email.enabled=true but spring.mail.username looks like a placeholder. Update application.properties with real SMTP credentials.");
        } else {
            System.out.printf("[EmailService] Ready — sending from %s via %s.%n", fromAddress, smtpUsername);
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

    // ── Internals ─────────────────────────────────────────────────────────────

    private void send(String to, String subject, String body) {
        if (!emailEnabled || mailSender == null || to == null || to.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            System.out.printf("[EmailService] Sent \"%s\" to %s%n", subject, to);
        } catch (Exception ex) {
            System.err.printf("[EmailService] Failed to send to %s: %s%n", to, ex.getMessage());
        }
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

