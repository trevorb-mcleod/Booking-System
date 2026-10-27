package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Sends transactional email via Brevo's HTTPS API (port 443).
 * Use on hosts that block outbound SMTP (e.g. Render free tier).
 */
@Component
public class BrevoApiClient {

    private static final URI SEND_URL = URI.create("https://api.brevo.com/v3/smtp/email");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${booking.brevo.api-key:}")
    private String apiKey;

    @PostConstruct
    public void logStatus() {
        if (isConfigured()) {
            System.out.println("[BrevoApiClient] Ready — transactional email via HTTPS API.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean send(String fromEmail, String fromName,
                        String toEmail, String toName,
                        String subject, String textBody) {
        if (!isConfigured()) return false;
        if (toEmail == null || toEmail.isBlank()) return false;

        String json = buildJson(fromEmail, fromName, toEmail, toName, subject, textBody);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(SEND_URL)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.printf("[BrevoApiClient] Sent \"%s\" to %s%n", subject, toEmail);
                return true;
            }
            System.err.printf("[BrevoApiClient] API error %d: %s%n",
                    response.statusCode(), truncate(response.body(), 500));
            return false;
        } catch (Exception ex) {
            System.err.printf("[BrevoApiClient] Failed to send to %s: %s%n", toEmail, ex.getMessage());
            return false;
        }
    }

    private static String buildJson(String fromEmail, String fromName,
                                    String toEmail, String toName,
                                    String subject, String textBody) {
        return "{"
                + "\"sender\":{\"email\":" + jsonString(fromEmail) + ",\"name\":" + jsonString(fromName) + "},"
                + "\"to\":[{\"email\":" + jsonString(toEmail) + ",\"name\":" + jsonString(toName) + "}],"
                + "\"subject\":" + jsonString(subject) + ","
                + "\"textContent\":" + jsonString(textBody)
                + "}";
    }

    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
