package org.example.model;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Represents a customer / client who books appointments.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    private String id;
    private String name;
    private String email;
    private String phone;

    /** Required by JPA. */
    protected Client() {}

    public Client(String name, String email, String phone) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                      { return id; }
    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }
    public String getEmail()                   { return email; }
    public void   setEmail(String email)       { this.email = email; }
    public String getPhone()                   { return phone; }
    public void   setPhone(String phone)       { this.phone = phone; }

    @Override
    public String toString() {
        return String.format("[%s] %-20s | %-28s | %s", id, name, email, phone);
    }
}
