package org.example.controller;

import org.example.manager.BookingManager;
import org.example.model.AppointmentStatus;
import org.example.model.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoints consumed by the booking form's JavaScript for dynamic
 * service and time-slot loading without full page reloads.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final BookingManager manager;

    public ApiController(BookingManager manager) {
        this.manager = manager;
    }

    /** Returns the services offered by a given provider (for dynamic dropdown). */
    @GetMapping("/providers/{id}/services")
    public List<Map<String, Object>> getProviderServices(@PathVariable String id) {
        return manager.findProviderById(id)
                .map(p -> p.getServiceIds().stream()
                        .map(sid -> manager.findServiceById(sid).orElse(null))
                        .filter(Objects::nonNull)
                        .map(this::serviceToMap)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    /** Returns all services across all providers — used when "Any Provider" is selected. */
    @GetMapping("/services")
    public List<Map<String, Object>> getAllServices() {
        return manager.getAllServices().stream()
                .map(this::serviceToMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> serviceToMap(Service s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",       s.getId());
        m.put("name",     s.getName());
        m.put("duration", s.getDurationMinutes());
        m.put("price",    s.getPrice());
        m.put("custom",   s.getName().toLowerCase().contains("other"));
        return m;
    }

    /** Returns available time slots as standard time strings e.g. "9:00 AM" (for slot picker). */
    @GetMapping("/slots")
    public List<String> getAvailableSlots(@RequestParam String providerId,
                                          @RequestParam String serviceId,
                                          @RequestParam String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            return manager.getAvailableSlots(providerId, serviceId, localDate)
                    .stream()
                    .map(dt -> dt.format(TIME_FMT))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Returns all appointments as FullCalendar-compatible event objects. */
    @GetMapping("/appointments/calendar")
    public List<Map<String, Object>> getCalendarEvents() {
        return manager.getAllAppointments().stream()
                .map(a -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id",              a.getId());
                    event.put("title",           a.getClient().getName() + " – " + a.getService().getName());
                    event.put("start",           a.getStartTime().toString());
                    event.put("end",             a.getEndTime().toString());
                    event.put("backgroundColor", statusColor(a.getStatus()));
                    event.put("borderColor",     statusColor(a.getStatus()));

                    Map<String, String> props = new LinkedHashMap<>();
                    props.put("provider", a.getProvider().getName());
                    props.put("service",  a.getService().getName());
                    props.put("status",   a.getStatus().toString());
                    props.put("notes",    a.getNotes());
                    event.put("extendedProps", props);
                    return event;
                })
                .collect(Collectors.toList());
    }

    private String statusColor(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> "#198754";
            case PENDING   -> "#e6a817";
            case CANCELLED -> "#dc3545";
            case COMPLETED -> "#6c757d";
        };
    }
}
