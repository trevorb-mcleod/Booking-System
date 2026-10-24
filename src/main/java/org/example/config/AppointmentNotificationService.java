package org.example.config;

import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.example.model.AppointmentStatus;
import org.example.model.ReminderChannel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Confirmation at booking time + ~24h reminders (email or SMS per appointment).
 * Appointments booked less than 24 hours ahead get SMS/email at booking instead of waiting for the scheduler.
 */
@Service
public class AppointmentNotificationService {

    private static final int MIN_LEAD_MINUTES = 5;

    private final BookingManager manager;
    private final EmailService   emailService;
    private final SmsService     smsService;

    public AppointmentNotificationService(BookingManager manager,
                                          EmailService emailService,
                                          SmsService smsService) {
        this.manager      = manager;
        this.emailService = emailService;
        this.smsService   = smsService;
    }

    /** Call immediately after a new appointment is saved. */
    public void onAppointmentBooked(Appointment a) {
        System.out.printf("[Notifications] Appointment %s booked — reminder channel: %s, sms to: %s%n",
                a.getId(),
                a.getReminderChannel(),
                maskPhone(SmsService.reminderDestination(a)));

        emailService.sendConfirmation(a);

        if (a.getReminderChannel() == ReminderChannel.SMS) {
            boolean smsOk = smsService.sendBookingConfirmation(a);
            if (!smsOk) {
                System.err.printf("[Notifications] SMS confirmation failed for appointment %s — check [SmsService] logs above.%n",
                        a.getId());
            } else if (isShortNotice(a)) {
                manager.markReminderSent(a.getId());
            }
        } else if (isShortNotice(a)) {
            sendReminderIfDue(a);
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "(none)";
        String d = phone.replaceAll("\\D", "");
        if (d.length() < 4) return "****";
        return "****" + d.substring(d.length() - 4);
    }

    /**
     * Hourly job: send ~24h reminders when within 25 hours of start, retrying until sent or too late.
     * Covers short-notice bookings if the immediate send at booking time failed.
     */
    public void sendScheduledReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (Appointment a : manager.getAllAppointments()) {
            if (!isReminderCandidate(a, now)) continue;
            long minutesUntil = Duration.between(now, a.getStartTime()).toMinutes();
            if (minutesUntil > 25 * 60L) continue;
            sendReminderIfDue(a);
        }
    }

    private void sendReminderIfDue(Appointment a) {
        if (a.isReminderSent() || a.getStatus() != AppointmentStatus.CONFIRMED) return;
        LocalDateTime now = LocalDateTime.now();
        if (!a.getStartTime().isAfter(now.plusMinutes(MIN_LEAD_MINUTES))) return;

        boolean sent = switch (a.getReminderChannel()) {
            case SMS -> smsService.sendReminder(a);
            case EMAIL -> emailService.sendReminder(a);
        };
        if (sent) {
            manager.markReminderSent(a.getId());
        }
    }

    private static boolean isShortNotice(Appointment a) {
        long minutesUntil = Duration.between(LocalDateTime.now(), a.getStartTime()).toMinutes();
        return minutesUntil > MIN_LEAD_MINUTES && minutesUntil < 23 * 60L;
    }

    private static boolean isReminderCandidate(Appointment a, LocalDateTime now) {
        return a.getStatus() == AppointmentStatus.CONFIRMED
                && !a.isReminderSent()
                && a.getStartTime().isAfter(now.plusMinutes(MIN_LEAD_MINUTES));
    }
}
