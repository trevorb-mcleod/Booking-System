package org.example.controller;

import org.example.manager.BookingManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/services")
public class ServiceController {

    private final BookingManager manager;

    public ServiceController(BookingManager manager) {
        this.manager = manager;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("services",    manager.getAllServices());
        model.addAttribute("currentPage", "services");
        return "admin/services/list";
    }

    @PostMapping
    public String add(@RequestParam String name,
                      @RequestParam String description,
                      @RequestParam int    durationMinutes,
                      @RequestParam double price,
                      RedirectAttributes  ra) {
        manager.addService(name.trim(), description.trim(), durationMinutes, price);
        ra.addFlashAttribute("successMsg", "Service \"" + name + "\" added.");
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable String id,
                       @RequestParam String name,
                       @RequestParam String description,
                       @RequestParam int    durationMinutes,
                       @RequestParam double price,
                       RedirectAttributes  ra) {
        if (manager.updateService(id, name.trim(), description.trim(), durationMinutes, price)) {
            ra.addFlashAttribute("successMsg", "Service \"" + name + "\" updated.");
        } else {
            ra.addFlashAttribute("errorMsg", "Service not found.");
        }
        return "redirect:/admin/services";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        if (manager.removeService(id)) {
            ra.addFlashAttribute("successMsg", "Service removed.");
        } else {
            ra.addFlashAttribute("errorMsg", "Service not found.");
        }
        return "redirect:/admin/services";
    }
}
