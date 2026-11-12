package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.admin.notification.NotificationService;
import com.example.project_flow.admin.notification.NotificationService.Inbox;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The bell menu for whoever is signed in. /api/admin/notifications is the earlier, admin-only address. */
@RestController
@RequestMapping({"/api/me/notifications", "/api/admin/notifications"})
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Inbox inbox(@RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        AppUser user = UserSession.user(request);
        return service.inbox(user.getId(), isAdmin(user), limit);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, HttpServletRequest request) {
        AppUser user = UserSession.user(request);
        service.markRead(user.getId(), isAdmin(user), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(HttpServletRequest request) {
        AppUser user = UserSession.user(request);
        service.markAllRead(user.getId(), isAdmin(user));
    }

    private static boolean isAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN;
    }
}
