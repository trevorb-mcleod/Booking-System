package org.example.controller;

import org.example.config.AppointmentNotificationService;
import org.example.config.EmailService;
import org.example.config.ReminderOptions;
import org.example.config.SmsService;
import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.example.model.Client;
import org.example.model.ReminderChannel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/appointments")
public class AppointmentController {

    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final BookingManager manager;
    private final EmailService   emailService;
    private final SmsService     smsService;
    private final AppointmentNotificationService notifications;
    private final ReminderOptions reminderOptions;

    public AppointmentController(BookingManager manager,
                                   EmailService emailService,
                                   SmsService smsService,
                                   AppointmentNotificationService notifications,
                                   ReminderOptions reminderOptions) {
        this.manager       = manager;
        this.emailService  = emailService;
        this.smsService    = smsService;
        this.reminderOptions = reminderOptions;
        this.notifications = notifications;
    }

    // ── List / filter ─────────────────────────────────────────────────────���──

    @GetMapping
    public String list(@RequestParam(required = false) String date,
                       @RequestParam(required = false) String providerId,
                       @RequestParam(required = false) String clientId,
                       Model model) {

        LocalDate filterDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : null;
        List<Appointment> appointments =
                manager.getAppointmentsFiltered(filterDate, providerId, clientId);
        String filterLabel = buildFilterLabel(filterDate, providerId, clientId);

        model.addAttribute("appointments", appointments);
        model.addAttribute("filterLabel",  filterLabel);
        model.addAttribute("providers",    manager.getAllProviders());
        model.addAttribute("clients",      manager.getAllClients());
        model.addAttribute("selectedDate",       date != null ? date : "");
        model.addAttribute("selectedProviderId", providerId != null ? providerId : "");
        model.addAttribute("selectedClientId",   clientId != null ? clientId : "");
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
        model.addAttribute("remindersEnabled", reminderOptions.remindersEnabled());
        model.addAttribute("smsRemindersAvailable", reminderOptions.smsChoiceAvailable());
        return "admin/appointments/book";
    }

    @PostMapping("/book")
    public String book(@RequestParam(required = false) String clientId,
                       @RequestParam(required = false) String newClientName,
                       @RequestParam(required = false) String newClientEmail,
                       @RequestParam(required = false) String newClientPhone,
                       @RequestParam(required = false, defaultValue = "EMAIL") String reminderChannel,
                       @RequestParam String providerId,
                       @RequestParam String serviceId,
                       @RequestParam String date,
                       @RequestParam String selectedTime,
                       RedirectAttributes  ra) {

        ReminderChannel channel = parseReminderChannel(reminderChannel);
        if (!reminderOptions.remindersEnabled()) {
            channel = ReminderChannel.EMAIL;
        } else if (channel == ReminderChannel.SMS && !reminderOptions.smsChoiceAvailable()) {
            ra.addFlashAttribute("errorMsg", "SMS reminders are not configured on this server.");
            return "redirect:/admin/appointments/book";
        }

        String reminderPhone = null;

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
            if (channel == ReminderChannel.SMS
                    && (newClientPhone == null || newClientPhone.isBlank())) {
                ra.addFlashAttribute("errorMsg", "A phone number is required for SMS reminders.");
                return "redirect:/admin/appointments/book";
            }
            if (channel == ReminderChannel.SMS) {
                reminderPhone = newClientPhone.trim();
            }
        } else {
            resolvedClientId = clientId;
            if (channel == ReminderChannel.SMS) {
                String phone = manager.findClientById(resolvedClientId)
                        .map(Client::getPhone)
                        .orElse("");
                if (phone == null || phone.isBlank()) {
                    ra.addFlashAttribute("errorMsg",
                            "This client has no phone number on file. Add one or choose email reminders.");
                    return "redirect:/admin/appointments/book";
                }
                reminderPhone = phone.trim();
            }
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

        Optional<Appointment> result = manager.bookAppointment(
                resolvedClientId, providerId, serviceId, startTime, "", channel, reminderPhone);

        if (result.isPresent()) {
            Appointment appt = manager.findAppointmentById(result.get().getId()).orElse(result.get());
            notifications.onAppointmentBooked(appt);
            ra.addFlashAttribute("successMsg",
                    "✅ Appointment confirmed! ID: " + appt.getId());
            return "redirect:/admin/appointments";
        } else {
            ra.addFlashAttribute("errorMsg",
                    "Could not book — scheduling conflict or invalid selection.");
            return "redirect:/admin/appointments/book";
        }
    }

    // ── Status updates ───────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String id,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String providerId,
                         @RequestParam(required = false) String clientId,
                         RedirectAttributes ra) {
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
        return redirectToList(date, providerId, clientId);
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable String id,
                           @RequestParam(required = false) String date,
                           @RequestParam(required = false) String providerId,
                           @RequestParam(required = false) String clientId,
                           RedirectAttributes ra) {
        if (manager.completeAppointment(id)) {
            ra.addFlashAttribute("successMsg", "Appointment marked as completed.");
        } else {
            ra.addFlashAttribute("errorMsg", "Could not complete — not found or wrong status.");
        }
        return redirectToList(date, providerId, clientId);
    }

    @PostMapping("/{id}/notes")
    public String notes(@PathVariable String id,
                        @RequestParam(required = false, defaultValue = "") String notes,
                        @RequestParam(required = false) String date,
                        @RequestParam(required = false) String providerId,
                        @RequestParam(required = false) String clientId,
                        RedirectAttributes ra) {
        if (manager.updateAppointmentNotes(id, notes)) {
            ra.addFlashAttribute("successMsg", "Notes saved.");
        } else {
            ra.addFlashAttribute("errorMsg", "Appointment not found.");
        }
        return redirectToList(date, providerId, clientId);
    }

    private static ReminderChannel parseReminderChannel(String value) {
        if (value == null || value.isBlank()) return ReminderChannel.EMAIL;
        try {
            return ReminderChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ReminderChannel.EMAIL;
        }
    }

    private String buildFilterLabel(LocalDate date, String providerId, String clientId) {
        List<String> parts = new ArrayList<>();
        if (date != null) {
            parts.add("on " + date.format(DATE_FMT));
        }
        if (providerId != null && !providerId.isBlank()) {
            manager.findProviderById(providerId)
                    .ifPresent(p -> parts.add("provider " + p.getName()));
        }
        if (clientId != null && !clientId.isBlank()) {
            manager.findClientById(clientId)
                    .ifPresent(c -> parts.add("client " + c.getName()));
        }
        if (parts.isEmpty()) {
            return "All Appointments";
        }
        return "Appointments " + String.join(" · ", parts);
    }

    private String redirectToList(String date, String providerId, String clientId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/appointments");
        if (date != null && !date.isBlank()) {
            builder.queryParam("date", date);
        }
        if (providerId != null && !providerId.isBlank()) {
            builder.queryParam("providerId", providerId);
        }
        if (clientId != null && !clientId.isBlank()) {
            builder.queryParam("clientId", clientId);
        }
        return "redirect:" + builder.build().toUriString();
    }
}

