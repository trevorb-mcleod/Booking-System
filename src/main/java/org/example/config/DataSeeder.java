package org.example.config;

import jakarta.annotation.PostConstruct;
import org.example.manager.BookingManager;
import org.example.model.Client;
import org.example.model.Provider;
import org.example.model.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Seeds demo data on startup.
 *
 * When deploying for a real client:
 *   1. Replace services with their actual offerings (cuts, sessions, consultations, etc.)
 *   2. Replace providers with real staff names / hours
 *   3. Remove or adjust the sample appointments below
 */
@Component
@Profile("!test")
public class DataSeeder {

    private final BookingManager manager;

    public DataSeeder(BookingManager manager) {
        this.manager = manager;
    }

    @PostConstruct
    public void seed() {
        // Skip seeding if data already exists (i.e. database was previously populated)
        if (!manager.getAllServices().isEmpty()) {
            System.out.println("[DataSeeder] Database already populated — skipping seed.");
            return;
        }

        // ── Services ─────────────────────────────────────────────────────────
        Service consultation   = manager.addService("Consultation",      "Free intro / discovery session",      15,   0.00);
        Service expressSession = manager.addService("Express Session",   "Quick 30-min service",                30,  45.00);
        Service standardSession= manager.addService("Standard Session",  "Core 60-min service",                 60,  75.00);
        Service premiumPackage = manager.addService("Premium Package",   "Full-service experience — 90 min",    90, 130.00);

        // ── Providers ────────────────────────────────────────────────────────
        Provider alex = manager.addProvider("Alex Johnson", "alex@yourbusiness.com", "555-0101",
                LocalTime.of(9, 0), LocalTime.of(17, 0));

        Provider sam = manager.addProvider("Sam Rivera", "sam@yourbusiness.com", "555-0102",
                LocalTime.of(10, 0), LocalTime.of(18, 0));

        // Assign services
        manager.assignServiceToProvider(alex.getId(), consultation.getId());
        manager.assignServiceToProvider(alex.getId(), expressSession.getId());
        manager.assignServiceToProvider(alex.getId(), standardSession.getId());
        manager.assignServiceToProvider(alex.getId(), premiumPackage.getId());

        manager.assignServiceToProvider(sam.getId(), consultation.getId());
        manager.assignServiceToProvider(sam.getId(), expressSession.getId());
        manager.assignServiceToProvider(sam.getId(), standardSession.getId());

        // ── Clients ──────────────────────────────────────────────────────────
        Client john = manager.addClient("John Doe",   "john@example.com",  "555-1001");
        Client jane = manager.addClient("Jane Smith", "jane@example.com",  "555-1002");
        Client mike = manager.addClient("Mike Brown", "mike@example.com",  "555-1003");

        // ── Sample appointments (today so they appear on the dashboard) ──────
        LocalDate today = LocalDate.now();

        manager.bookAppointment(john.getId(), alex.getId(), expressSession.getId(),
                LocalDateTime.of(today, LocalTime.of(10, 0)));

        manager.bookAppointment(jane.getId(), alex.getId(), standardSession.getId(),
                LocalDateTime.of(today, LocalTime.of(11, 0)));

        manager.bookAppointment(mike.getId(), sam.getId(), consultation.getId(),
                LocalDateTime.of(today, LocalTime.of(10, 30)));
    }
}
