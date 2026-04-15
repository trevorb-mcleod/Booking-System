package org.example.controller;

import org.example.manager.BookingManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/admin/providers")
public class ProviderController {

    private final BookingManager manager;

    public ProviderController(BookingManager manager) {
        this.manager = manager;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("providers",   manager.getAllProviders());
        model.addAttribute("services",    manager.getAllServices());
        model.addAttribute("currentPage", "providers");
        return "admin/providers/list";
    }

    @PostMapping
    public String add(@RequestParam String name,
                      @RequestParam String email,
                      @RequestParam String phone,
                      @RequestParam String workStart,
                      @RequestParam String workEnd,
                      RedirectAttributes  ra) {
        try {
            manager.addProvider(name.trim(), email.trim(), phone.trim(),
                    LocalTime.parse(workStart), LocalTime.parse(workEnd));
            ra.addFlashAttribute("successMsg", "Provider \"" + name + "\" added.");
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("errorMsg", "Invalid time format — use HH:mm.");
        }
        return "redirect:/admin/providers";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable String id,
                       @RequestParam String name,
                       @RequestParam String email,
                       @RequestParam String phone,
                       @RequestParam String workStart,
                       @RequestParam String workEnd,
                       RedirectAttributes ra) {
        try {
            if (manager.updateProvider(id, name.trim(), email.trim(), phone.trim(),
                    LocalTime.parse(workStart), LocalTime.parse(workEnd))) {
                ra.addFlashAttribute("successMsg", "Provider \"" + name + "\" updated.");
            } else {
                ra.addFlashAttribute("errorMsg", "Provider not found.");
            }
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("errorMsg", "Invalid time format — use HH:mm.");
        }
        return "redirect:/admin/providers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        if (manager.removeProvider(id)) {
            ra.addFlashAttribute("successMsg", "Provider removed.");
        } else {
            ra.addFlashAttribute("errorMsg", "Provider not found.");
        }
        return "redirect:/admin/providers";
    }

    @PostMapping("/{id}/assign-service")
    public String assignService(@PathVariable String id,
                                @RequestParam  String serviceId,
                                RedirectAttributes   ra) {
        if (manager.assignServiceToProvider(id, serviceId)) {
            ra.addFlashAttribute("successMsg", "Service assigned.");
        } else {
            ra.addFlashAttribute("errorMsg", "Invalid provider or service ID.");
        }
        return "redirect:/admin/providers";
    }
}
