package org.example.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a service provider (barber, trainer, consultant, etc.).
 * Work hours define when appointment slots are generated.
 */
@Entity
@Table(name = "providers")
public class Provider {

    @Id
    private String id;
    private String name;
    private String email;
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "provider_services", joinColumns = @JoinColumn(name = "provider_id"))
    @Column(name = "service_id")
    private List<String> serviceIds = new ArrayList<>();

    private LocalTime workdayStart;
    private LocalTime workdayEnd;

    /** Required by JPA. */
    protected Provider() {}

    public Provider(String name, String email, String phone,
                    LocalTime workdayStart, LocalTime workdayEnd) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.workdayStart = workdayStart;
        this.workdayEnd = workdayEnd;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String       getId()                        { return id; }
    public String       getName()                      { return name; }
    public void         setName(String name)           { this.name = name; }
    public String       getEmail()                     { return email; }
    public void         setEmail(String email)         { this.email = email; }
    public String       getPhone()                     { return phone; }
    public void         setPhone(String phone)         { this.phone = phone; }
    public List<String> getServiceIds()                { return serviceIds; }
    public LocalTime    getWorkdayStart()              { return workdayStart; }
    public void         setWorkdayStart(LocalTime t)   { this.workdayStart = t; }
    public LocalTime    getWorkdayEnd()                { return workdayEnd; }
    public void         setWorkdayEnd(LocalTime t)     { this.workdayEnd = t; }

    public void addService(String serviceId) {
        if (!serviceIds.contains(serviceId)) serviceIds.add(serviceId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %-18s | %-28s | %-12s | Hours: %s–%s",
                id, name, email, phone, workdayStart, workdayEnd);
    }
}
