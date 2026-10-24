package org.example.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends ~24-hour appointment reminders on each client's chosen channel (email or SMS).
 * Active when booking.reminders.enabled=true.
 */
@Component
@EnableScheduling
@DependsOn("sqliteSchemaPatcher")
@ConditionalOnProperty(name = "booking.reminders.enabled", havingValue = "true")
public class ReminderService {

    private final AppointmentNotificationService notifications;

    public ReminderService(AppointmentNotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void sendReminders() {
        notifications.sendScheduledReminders();
    }
}
