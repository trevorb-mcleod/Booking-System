package org.example.controller;

import org.example.manager.BookingManager;
import org.example.model.Appointment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final BookingManager manager;

    public DashboardController(BookingManager manager) {
        this.manager = manager;
    }

    @GetMapping("/admin/")
    public String dashboard(Model model) {
        List<Appointment> todayAppts = manager.getAppointmentsByDate(LocalDate.now());

        model.addAttribute("totalClients",      manager.getAllClients().size());
        model.addAttribute("totalProviders",    manager.getAllProviders().size());
        model.addAttribute("totalServices",     manager.getAllServices().size());
        model.addAttribute("totalAppointments", manager.getAllAppointments().size());
        model.addAttribute("todayAppointments", todayAppts);
        model.addAttribute("todayCount",        todayAppts.size());
        model.addAttribute("currentPage", "dashboard");
        return "admin/dashboard";
    }

}
