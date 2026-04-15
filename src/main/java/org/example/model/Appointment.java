package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * A booked appointment linking a client, provider, and service to a specific date/time.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    private String notes;

    /** Unique token used in self-service cancellation links — never exposed as the main ID. */
    private String cancelToken;

    /** Required by JPA. */
    protected Appointment() {}

    public Appointment(Client client, Provider provider, Service service, LocalDateTime startTime) {
        this.id          = UUID.randomUUID().toString().substring(0, 8);
        this.cancelToken = UUID.randomUUID().toString();
        this.client    = client;
        this.provider  = provider;
        this.service   = service;
        this.startTime = startTime;
        this.endTime   = startTime.plusMinutes(service.getDurationMinutes());
        this.status    = AppointmentStatus.PENDING;
        this.notes     = "";
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String           getId()                        { return id; }
    public Client           getClient()                    { return client; }
    public Provider         getProvider()                  { return provider; }
    public Service          getService()                   { return service; }
    public LocalDateTime    getStartTime()                 { return startTime; }
    public LocalDateTime    getEndTime()                   { return endTime; }
    public AppointmentStatus getStatus()                   { return status; }
    public void             setStatus(AppointmentStatus s) { this.status = s; }
    public String           getNotes()                     { return notes; }
    public void             setNotes(String notes)         { this.notes = notes; }
    public String           getCancelToken()               { return cancelToken; }

    @Override
    public String toString() {
        String noteStr = notes.isBlank() ? "" : " | Notes: " + notes;
        return String.format("[%s] %-22s with %-16s (Client: %-16s) | %s – %s | %s%s",
                id,
                service.getName(),
                provider.getName(),
                client.getName(),
                startTime.format(FMT),
                endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                status,
                noteStr);
    }
}
