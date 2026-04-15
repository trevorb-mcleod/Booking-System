package org.example.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Represents a bookable service offered by a provider.
 * Customize name/description/duration/price per business type.
 */
@Entity
@Table(name = "services")
public class Service {

    @Id
    private String id;
    private String name;
    private String description;
    private int durationMinutes;
    private double price;

    /** Required by JPA. */
    protected Service() {}

    public Service(String name, String description, int durationMinutes, double price) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public void   setName(String name)             { this.name = name; }
    public String getDescription()                 { return description; }
    public void   setDescription(String d)         { this.description = d; }
    public int    getDurationMinutes()             { return durationMinutes; }
    public void   setDurationMinutes(int d)        { this.durationMinutes = d; }
    public double getPrice()                       { return price; }
    public void   setPrice(double price)           { this.price = price; }

    @Override
    public String toString() {
        return String.format("[%s] %-22s | %-35s | %3d min | $%.2f",
                id, name, description, durationMinutes, price);
    }
}
