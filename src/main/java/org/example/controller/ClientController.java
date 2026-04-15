package org.example.controller;

import org.example.manager.BookingManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Controller
@RequestMapping("/admin/clients")
public class ClientController {

    private final BookingManager manager;

    public ClientController(BookingManager manager) {
        this.manager = manager;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clients",     manager.getAllClients());
        model.addAttribute("currentPage", "clients");
        return "admin/clients/list";
    }

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"clients.csv\"");
        PrintWriter w = response.getWriter();
        w.println("ID,Name,Email,Phone");
        manager.getAllClients().forEach(c ->
            w.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    c.getId(),
                    c.getName(),
                    c.getEmail(),
                    c.getPhone()));
    }

    @PostMapping
    public String register(@RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String phone,
                           RedirectAttributes  ra) {
        manager.addClient(name.trim(), email.trim(), phone.trim());
        ra.addFlashAttribute("successMsg", "Client \"" + name + "\" registered.");
        return "redirect:/admin/clients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        if (manager.removeClient(id)) {
            ra.addFlashAttribute("successMsg", "Client removed.");
        } else {
            ra.addFlashAttribute("errorMsg", "Client not found.");
        }
        return "redirect:/admin/clients";
    }
}
