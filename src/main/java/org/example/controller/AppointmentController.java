package org.example.controller;

import org.example.config.EmailService;
import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.example.model.Client;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/appointments")
public class AppointmentController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final BookingManager manager;
    private final EmailService   emailService;

    public AppointmentController(BookingManager manager, EmailService emailService) {
        this.manager      = manager;
        this.emailService = emailService;
    }

    // ── List / filter ─────────────────────────────────────────────────────���──

    @GetMapping
    public String list(@RequestParam(required = false) String date,
                       @RequestParam(required = false) String providerId,
                       @RequestParam(required = false) String clientId,
                       Model model) {

        List<Appointment> appointments;
        String filterLabel = "All Appointments";

        if (date != null && !date.isBlank()) {
            appointments = manager.getAppointmentsByDate(LocalDate.parse(date));
            filterLabel  = "Appointments on " + date;
        } else if (providerId != null && !providerId.isBlank()) {
            appointments = manager.getAppointmentsByProvider(providerId);
            filterLabel  = "Appointments by Provider";
        } else if (clientId != null && !clientId.isBlank()) {
            appointments = manager.getAppointmentsByClient(clientId);
            filterLabel  = "Appointments by Client";
        } else {
            appointments = manager.getAllAppointments();
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("filterLabel",  filterLabel);
        model.addAttribute("providers",    manager.getAllProviders());
        model.addAttribute("clients",      manager.getAllClients());
        model.addAttribute("currentPage",  "appointments");
        return "admin/appointments/list";
    }

    // ── Calendar view ─────────────────────────────────────────────────────────

    @GetMapping("/calendar")
    public String calendar() {
        return "admin/appointments/calendar";
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"appointments.csv\"");
        PrintWriter w = response.getWriter();
        w.println("ID,Date,Time,Client,Provider,Service,Duration (min),Price ($),Status,Notes");
        for (Appointment a : manager.getAllAppointments()) {
            w.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%.2f,\"%s\",\"%s\"%n",
                    a.getId(),
                    a.getStartTime().toLocalDate(),
                    a.getStartTime().toLocalTime().format(TIME_FMT),
                    a.getClient().getName(),
                    a.getProvider().getName(),
                    a.getService().getName(),
                    a.getService().getDurationMinutes(),
                    a.getService().getPrice(),
                    a.getStatus(),
                    a.getNotes().replace("\"", "\"\""));
        }
    }

    // ── Booking form ─────────────────────────────────────────────────────────

    @GetMapping("/book")
    public String bookForm(Model model) {
        model.addAttribute("clients",     manager.getAllClients());
        model.addAttribute("providers",   manager.getAllProviders());
        model.addAttribute("currentPage", "appointments");
        return "admin/appointments/book";
    }

    @PostMapping("/book")
    public String book(@RequestParam(required = false) String clientId,
                       @RequestParam(required = false) String newClientName,
                       @RequestParam(required = false) String newClientEmail,
                       @RequestParam(required = false) String newClientPhone,
                       @RequestParam String providerId,
                       @RequestParam String serviceId,
                       @RequestParam String date,
                       @RequestParam String selectedTime,
                       RedirectAttributes  ra) {

        // Resolve client
        String resolvedClientId;
        if ("NEW".equals(clientId)) {
            if (newClientName == null || newClientName.isBlank()) {
                ra.addFlashAttribute("errorMsg", "Client name is required.");
                return "redirect:/admin/appointments/book";
            }
            Client c = manager.addClient(newClientName.trim(),
                    newClientEmail == null ? "" : newClientEmail.trim(),
                    newClientPhone == null ? "" : newClientPhone.trim());
            resolvedClientId = c.getId();
        } else {
            resolvedClientId = clientId;
        }

        // Parse date + time
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.of(LocalDate.parse(date),
                    LocalTime.parse(selectedTime, TIME_FMT));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Invalid date or time.");
            return "redirect:/admin/appointments/book";
        }

        Optional<Appointment> result =
                manager.bookAppointment(resolvedClientId, providerId, serviceId, startTime);

        if (result.isPresent()) {
            emailService.sendConfirmation(result.get());
            ra.addFlashAttribute("successMsg",
                    "✅ Appointment confirmed! ID: " + result.get().getId());
            return "redirect:/admin/appointments";
        } else {
            ra.addFlashAttribute("errorMsg",
                    "Could not book — scheduling conflict or invalid selection.");
            return "redirect:/admin/appointments/book";
        }
    }

    // ── Status updates ───────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String id, RedirectAttributes ra) {
        manager.findAppointmentById(id).ifPresent(a -> {
            if (manager.cancelAppointment(id)) {
                emailService.sendCancellation(a);
                ra.addFlashAttribute("successMsg", "Appointment cancelled.");
            } else {
                ra.addFlashAttribute("errorMsg", "Could not cancel — not found or already cancelled.");
            }
        });
        if (!ra.getFlashAttributes().containsKey("successMsg") &&
            !ra.getFlashAttributes().containsKey("errorMsg")) {
            ra.addFlashAttribute("errorMsg", "Could not cancel — not found or already cancelled.");
        }
        return "redirect:/admin/appointments";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable String id, RedirectAttributes ra) {
        if (manager.completeAppointment(id)) {
            ra.addFlashAttribute("successMsg", "Appointment marked as completed.");
        } else {
            ra.addFlashAttribute("errorMsg", "Could not complete — not found or wrong status.");
        }
        return "redirect:/admin/appointments";
    }

    @PostMapping("/{id}/notes")
    public String notes(@PathVariable String id,
                        @RequestParam  String notes,
                        RedirectAttributes ra) {
        manager.findAppointmentById(id).ifPresentOrElse(
                a -> {
                    a.setNotes(notes);
                    ra.addFlashAttribute("successMsg", "Notes saved.");
                },
                () -> ra.addFlashAttribute("errorMsg", "Appointment not found.")
        );
        return "redirect:/admin/appointments";
    }
}

