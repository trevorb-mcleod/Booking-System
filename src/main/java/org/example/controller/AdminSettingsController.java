package org.example.controller;

import org.example.config.AdminSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private final AdminSettings adminSettings;

    public AdminSettingsController(AdminSettings adminSettings) {
        this.adminSettings = adminSettings;
    }

    @GetMapping
    public String settingsPage(Model model) {
        model.addAttribute("currentPage", "settings");
        return "admin/settings";
    }

    @PostMapping("/pin")
    public String updatePin(@RequestParam String currentPin,
                            @RequestParam String newPin,
                            @RequestParam String confirmPin,
                            RedirectAttributes ra) {

        if (!adminSettings.getPin().equals(currentPin.trim())) {
            ra.addFlashAttribute("errorMsg", "Current PIN is incorrect.");
            return "redirect:/admin/settings";
        }
        if (newPin.isBlank() || newPin.length() < 4) {
            ra.addFlashAttribute("errorMsg", "New PIN must be at least 4 characters.");
            return "redirect:/admin/settings";
        }
        if (!newPin.equals(confirmPin)) {
            ra.addFlashAttribute("errorMsg", "New PIN and confirmation do not match.");
            return "redirect:/admin/settings";
        }

        adminSettings.setPin(newPin.trim());
        ra.addFlashAttribute("successMsg", "PIN updated successfully.");
        return "redirect:/admin/settings";
    }
}

