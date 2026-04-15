package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.config.AdminSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminAuthController {

    private static final int MAX_ATTEMPTS = 5;

    private final AdminSettings adminSettings;

    public AdminAuthController(AdminSettings adminSettings) {
        this.adminSettings = adminSettings;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            HttpSession session,
                            Model model) {
        int attempts = getAttempts(session);
        model.addAttribute("hasError",  error != null);
        model.addAttribute("locked",    attempts >= MAX_ATTEMPTS);
        model.addAttribute("remaining", Math.max(0, MAX_ATTEMPTS - attempts));
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String pin, HttpSession session) {
        int attempts = getAttempts(session);

        if (attempts >= MAX_ATTEMPTS) {
            return "redirect:/admin/login?error";
        }

        if (adminSettings.getPin().equals(pin.trim())) {
            session.removeAttribute("loginAttempts");
            session.setAttribute("adminLoggedIn", true);
            return "redirect:/admin/";
        }

        session.setAttribute("loginAttempts", attempts + 1);
        return "redirect:/admin/login?error";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private int getAttempts(HttpSession session) {
        Object val = session.getAttribute("loginAttempts");
        return val instanceof Integer ? (Integer) val : 0;
    }
}
