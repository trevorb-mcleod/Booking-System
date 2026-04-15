package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects branding values from application.properties into every view model
 * so templates can use ${businessName} and ${tagline} instead of hardcoded strings.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @Value("${booking.business-name:BookSmart}")
    private String businessName;

    @Value("${booking.tagline:Appointment Booking Made Easy}")
    private String tagline;

    @ModelAttribute("businessName")
    public String businessName() { return businessName; }

    @ModelAttribute("tagline")
    public String tagline() { return tagline; }
}

