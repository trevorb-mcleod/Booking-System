package org.example.controller;

import org.example.config.EmailService;
import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.example.model.Client;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.example.model.AppointmentStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Public-facing customer portal.
 * No authentication required — customers book appointments here.
 */
@Controller
public class CustomerController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter ICAL_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final BookingManager manager;
    private final EmailService   emailService;

    @Value("${booking.business-name:BookSmart}")
    private String businessName;

    public CustomerController(BookingManager manager, EmailService emailService) {
        this.manager      = manager;
        this.emailService = emailService;
    }

    /** Public home page: shows providers, services, and a "Book Now" CTA. */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("providers", manager.getAllProviders());
        model.addAttribute("services",  manager.getAllServices());
        return "customer/home";
    }

    /** Public team page: lists each provider and the services they offer. */
    @GetMapping("/team")
    public String team(Model model) {
        model.addAttribute("providers", manager.getAllProviders());
        model.addAttribute("services",  manager.getAllServices());
        return "customer/team";
    }

    /** Public booking form. */
    @GetMapping("/book")
    public String bookForm(@RequestParam(required = false) String provider, Model model) {
        model.addAttribute("providers",          manager.getAllProviders());
        model.addAttribute("selectedProviderId", provider != null ? provider : "");
        return "customer/book";
    }

    /** Process the booking form. Finds or creates a client by email. */
    @PostMapping("/book")
    public String book(@RequestParam String customerName,
                       @RequestParam String customerEmail,
                       @RequestParam String customerPhone,
                       @RequestParam String providerId,
                       @RequestParam String serviceId,
                       @RequestParam String date,
                       @RequestParam String selectedTime,
                       @RequestParam(required = false, defaultValue = "") String notes,
                       RedirectAttributes  ra) {

        // Find existing client by email, or create a new one
        Client client = manager.findClientByEmail(customerEmail.trim())
                .orElseGet(() -> manager.addClient(
                        customerName.trim(),
                        customerEmail.trim(),
                        customerPhone.trim()));

        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.of(
                    LocalDate.parse(date),
                    LocalTime.parse(selectedTime, TIME_FMT));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Invalid date or time. Please try again.");
            return "redirect:/book";
        }

        Optional<Appointment> result =
                manager.bookAppointment(client.getId(), providerId, serviceId, startTime, notes);

        if (result.isPresent()) {
            Appointment appt = result.get();
            emailService.sendConfirmation(appt);
            ra.addFlashAttribute("apptId",          appt.getId());
            ra.addFlashAttribute("apptCancelToken",  appt.getCancelToken());
            ra.addFlashAttribute("apptProvider",    appt.getProvider().getName());
            ra.addFlashAttribute("apptService",     appt.getService().getName());
            ra.addFlashAttribute("apptDate",        appt.getStartTime().format(
                    DateTimeFormatter.ofPattern("MMMM d, yyyy")));
            ra.addFlashAttribute("apptTime",        appt.getStartTime().format(
                    DateTimeFormatter.ofPattern("h:mm a")));
            ra.addFlashAttribute("apptEndTime",     appt.getEndTime().format(
                    DateTimeFormatter.ofPattern("h:mm a")));
            ra.addFlashAttribute("apptClientName",  appt.getClient().getName());
            return "redirect:/confirmation";
        } else {
            ra.addFlashAttribute("errorMsg",
                    "That time slot is no longer available. Please choose another.");
            return "redirect:/book";
        }
    }

    /** Booking confirmation page. */
    @GetMapping("/confirmation")
    public String confirmation(Model model) {
        if (!model.containsAttribute("apptId")) {
            return "redirect:/";
        }
        return "customer/confirmation";
    }

    /** Download an .ics calendar file for the given appointment. */
    @GetMapping("/ical/{id}")
    public void downloadIcal(@PathVariable String id, HttpServletResponse response) throws IOException {
        Optional<Appointment> opt = manager.findAppointmentById(id);
        if (opt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Appointment a = opt.get();
        String now = LocalDateTime.now().format(ICAL_FMT) + "Z";
        String ics =
                "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//" + businessName + "//" + businessName + "//EN\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + a.getId() + "@booksmart\r\n" +
                "DTSTAMP:" + now + "\r\n" +
                "DTSTART:" + a.getStartTime().format(ICAL_FMT) + "\r\n" +
                "DTEND:" + a.getEndTime().format(ICAL_FMT) + "\r\n" +
                "SUMMARY:" + a.getService().getName() + " with " + a.getProvider().getName() + "\r\n" +
                "DESCRIPTION:Confirmation #: " + a.getId() +
                        "\\nProvider: " + a.getProvider().getName() +
                        "\\nService: " + a.getService().getName() +
                        "\\nBusiness: " + businessName + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";

        response.setContentType("text/calendar");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"appointment-" + a.getId() + ".ics\"");
        response.getWriter().write(ics);
    }

    // ── Self-service cancellation ─────────────────────────────────────────────

    /** Shows cancellation confirmation page, or success/error state. */
    @GetMapping("/cancel/{token}")
    public String cancelPage(@PathVariable String token, Model model) {
        Optional<Appointment> opt = manager.findAppointmentByCancelToken(token);
        if (opt.isEmpty()) {
            model.addAttribute("invalid", true);
        } else {
            Appointment a = opt.get();
            model.addAttribute("appt",        a);
            model.addAttribute("isConfirmed", a.getStatus() == AppointmentStatus.CONFIRMED);
            model.addAttribute("isCancelled", a.getStatus() == AppointmentStatus.CANCELLED);
        }
        return "customer/cancel";
    }

    /** Processes the cancellation and redirects back to the cancel page. */
    @PostMapping("/cancel/{token}")
    public String processCancel(@PathVariable String token, RedirectAttributes ra) {
        Optional<Appointment> opt = manager.findAppointmentByCancelToken(token);
        if (opt.isEmpty()) return "redirect:/";

        Appointment a = opt.get();
        if (a.getStatus() != AppointmentStatus.CANCELLED) {
            manager.cancelAppointment(a.getId());
            emailService.sendCancellation(a);
            ra.addFlashAttribute("justCancelled", true);
        }
        return "redirect:/cancel/" + token;
    }
}

