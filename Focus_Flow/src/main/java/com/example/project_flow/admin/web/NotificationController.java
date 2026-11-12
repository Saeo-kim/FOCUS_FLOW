package com.example.project_flow.admin.web;

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

@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Inbox inbox(@RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        return service.inbox(AdminSession.user(request).getId(), limit);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, HttpServletRequest request) {
        service.markRead(AdminSession.user(request).getId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(HttpServletRequest request) {
        service.markAllRead(AdminSession.user(request).getId());
    }
}
