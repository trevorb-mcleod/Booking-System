package org.example.config;

import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.example.model.AppointmentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends a reminder email to customers ~24 hours before their appointment.
 * Only active when booking.reminders.enabled=true in application.properties.
 *
 * To enable:
 *   1. Set booking.reminders.enabled=true
 *   2. Fill in spring.mail.* SMTP credentials
 *   3. Set booking.reminders.from to your sender address
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "booking.reminders.enabled", havingValue = "true")
public class ReminderService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final BookingManager  manager;
    private final JavaMailSender  mailSender;

    @Value("${booking.reminders.from:no-reply@yourbusiness.com}")
    private String fromAddress;

    @Value("${booking.business-name:BookSmart}")
    private String businessName;

    @Value("${booking.base-url:http://localhost:8080}")
    private String baseUrl;

    public ReminderService(BookingManager manager, JavaMailSender mailSender) {
        this.manager    = manager;
        this.mailSender = mailSender;
    }

    /**
     * Runs every hour. Finds confirmed appointments whose start time falls
     * within the next 23–25 hour window and emails the client a reminder.
     */
    @Scheduled(fixedDelay = 3_600_000) // every hour
    public void sendReminders() {
        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(23);
        LocalDateTime windowEnd   = now.plusHours(25);

        List<Appointment> upcoming = manager.getAllAppointments().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> a.getStartTime().isAfter(windowStart)
                          && a.getStartTime().isBefore(windowEnd))
                .toList();

        for (Appointment a : upcoming) {
            String email = a.getClient().getEmail();
            if (email == null || email.isBlank()) continue;

            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject("Reminder: Your appointment tomorrow at " + businessName);
                msg.setText(buildBody(a));
                mailSender.send(msg);
                System.out.printf("[ReminderService] Sent reminder to %s for appointment %s%n",
                        email, a.getId());
            } catch (Exception ex) {
                System.err.printf("[ReminderService] Failed to send reminder to %s: %s%n",
                        email, ex.getMessage());
            }
        }
    }

    private String buildBody(Appointment a) {
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
                businessName
        );
    }
}

