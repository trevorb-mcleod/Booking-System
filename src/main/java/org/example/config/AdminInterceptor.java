package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protects all /admin/** routes.
 * If the session doesn't have adminLoggedIn=true, redirect to /admin/login.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {

        String uri = request.getRequestURI();

        // Always allow the login/logout pages through
        if (uri.startsWith("/admin/login") || uri.equals("/admin/logout")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Boolean loggedIn = session != null
                ? (Boolean) session.getAttribute("adminLoggedIn")
                : null;

        if (loggedIn == null || !loggedIn) {
            response.sendRedirect("/admin/login");
            return false;
        }
        return true;
    }
}

