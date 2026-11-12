package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Guards /api/admin/** only; every other FOCUS FLOW API stays exactly as open as before.
 * Changes must also carry the X-FocusFlow-Admin header, which another site cannot add to a
 * cross-site request, so a signed-in browser cannot be tricked into approving or deleting.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String CSRF_HEADER = "X-FocusFlow-Admin";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/admin/auth/status", "/api/admin/auth/setup", "/api/admin/auth/login", "/api/admin/auth/logout");

    private final AccountService accounts;

    public AdminAuthInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String method = request.getMethod();
        boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        if (!safe && request.getHeader(CSRF_HEADER) == null) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "This request was blocked. Reload the admin page and try again.");
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        AppUser user = accounts.find(AdminSession.userId(request))
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .orElse(null);
        if (user == null) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in as the administrator to continue.");
        }
        request.setAttribute(AdminSession.REQUEST_USER, user);
        return true;
    }

    private static boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
        return false;
    }
}
