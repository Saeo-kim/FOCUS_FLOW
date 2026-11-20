package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.legal.web.LegalIntegrationController;
import com.example.project_flow.security.web.SecurityIntegrationController;
import org.springframework.web.method.HandlerMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Server-side authorization for every /api/** request.
 * <ul>
 *   <li>Sign-in endpoints are public.</li>
 *   <li>/api/member/** and /api/me/** serve any signed-in user, always scoped to that user.</li>
 *   <li>Everything else, including every API FOCUS FLOW had before accounts existed, is ADMIN only.</li>
 * </ul>
 * Changes to the account, admin and member APIs must also carry the X-FocusFlow header, which
 * another site cannot add to a cross-site request. The older APIs rely on the SameSite session cookie.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String CSRF_HEADER = "X-FocusFlow";
    static final String LEGACY_CSRF_HEADER = "X-FocusFlow-Admin";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/status", "/api/auth/setup", "/api/auth/login", "/api/auth/logout", "/api/auth/invitation",
            "/api/admin/auth/status", "/api/admin/auth/setup", "/api/admin/auth/login", "/api/admin/auth/logout");

    private final AccountService accounts;

    public AuthInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        boolean newApi = path.startsWith("/api/auth/") || path.startsWith("/api/admin/")
                || path.startsWith("/api/member/") || path.startsWith("/api/me/");
        if (!safe && newApi && request.getHeader(CSRF_HEADER) == null && request.getHeader(LEGACY_CSRF_HEADER) == null) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "This request was blocked. Reload the page and try again.");
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // Only the Legal integration handlers skip the session check. They authenticate with their own
        // service token, and nothing else is exempted: matching is on the resolved handler, not the URL text.
        if (handler instanceof HandlerMethod handlerMethod && (handlerMethod.getBeanType() == LegalIntegrationController.class
                || handlerMethod.getBeanType() == SecurityIntegrationController.class)) {
            return true;
        }
        AppUser user = accounts.activeUser(UserSession.userId(request)).orElse(null);
        if (user == null) {
            UserSession.signOut(request); // a deactivated or deleted account loses its session at once
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to continue.");
        }
        boolean personal = path.startsWith("/api/member/") || path.startsWith("/api/me/")
                || path.equals("/api/auth/profile") || path.equals("/api/auth/password")
                || path.equals("/api/admin/auth/profile") || path.equals("/api/admin/auth/password");
        if (!personal && user.getRole() != UserRole.ADMIN) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "This area is only for administrators.");
        }
        request.setAttribute(UserSession.REQUEST_USER, user);
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
