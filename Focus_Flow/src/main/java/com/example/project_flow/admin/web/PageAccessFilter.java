package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Stops the workspace pages from being opened by their file names, which would skip
 * {@link AdminPageController}. Only direct browser requests are checked; the controller's own
 * forwards pass. Scripts and stylesheets stay public because they hold no data.
 */
@Component
public class PageAccessFilter extends OncePerRequestFilter {

    private static final Map<String, UserRole> PAGES = Map.of(
            "/index.html", UserRole.ADMIN,
            "/admin-dashboard.html", UserRole.ADMIN,
            "/member.html", UserRole.MEMBER);

    private final AccountService accounts;

    public PageAccessFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PAGES.containsKey(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UserRole needed = PAGES.get(request.getRequestURI().substring(request.getContextPath().length()));
        AppUser user = accounts.activeUser(UserSession.userId(request)).orElse(null);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (user.getRole() != needed) {
            response.sendRedirect(request.getContextPath() + AccountService.homeOf(user));
            return;
        }
        chain.doFilter(request, response);
    }
}
