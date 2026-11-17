package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.service.References;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

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
    private final AccountService accounts;

    public NotificationController(NotificationService service, AccountService accounts) {
        this.service = service;
        this.accounts = accounts;
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

    /** Administrators only (the /api/admin path is guarded). Every active account gets a copy. */
    @PostMapping("/announce")
    @ResponseStatus(HttpStatus.CREATED)
    public void announce(@Valid @RequestBody AnnouncementRequest body, HttpServletRequest request) {
        if (!isAdmin(UserSession.user(request))) {
            throw new ConflictException("Only administrators can post announcements.");
        }
        List<Long> ids = accounts.listUsers().stream().filter(UserResponse::active).map(UserResponse::id).toList();
        service.announce(ids, body.title().trim(), References.clean(body.message()));
    }

    public record AnnouncementRequest(
            @NotBlank(message = "Enter a headline.") @Size(max = 200, message = "Keep the headline under 200 characters.") String title,
            @Size(max = 500, message = "Keep the message under 500 characters.") String message) {
    }

    private static boolean isAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN;
    }
}
