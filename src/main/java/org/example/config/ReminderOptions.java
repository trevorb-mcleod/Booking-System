package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether ~24h reminder choices are offered and SMS is a valid option.
 */
@Component
public class ReminderOptions {

    private final SmsService smsService;

    @Value("${booking.reminders.enabled:true}")
    private boolean remindersEnabled;

    public ReminderOptions(SmsService smsService) {
        this.smsService = smsService;
    }

    public boolean remindersEnabled() {
        return remindersEnabled;
    }

    /** SMS ~24h reminders (requires scheduler + Twilio). */
    public boolean smsChoiceAvailable() {
        return remindersEnabled && smsService.isConfigured();
    }
}
