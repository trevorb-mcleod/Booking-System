package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the admin PIN at runtime.
 * Initialised from application.properties (booking.admin.pin) but can be
 * changed live through the Settings page without restarting the server.
 * NOTE: changes are in-memory only – the original value in
 * application.properties is not modified.
 */
@Component
public class AdminSettings {

    private String pin;

    public AdminSettings(@Value("${booking.admin.pin:1234}") String pin) {
        this.pin = pin;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}

