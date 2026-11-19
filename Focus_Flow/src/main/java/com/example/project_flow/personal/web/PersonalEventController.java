package com.example.project_flow.personal.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.web.UserSession;
import com.example.project_flow.personal.PersonalEventService;
import com.example.project_flow.personal.PersonalEventService.Request;
import com.example.project_flow.personal.PersonalEventService.View;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** The signed-in person's own calendar items. Any signed-in account may use it; it sees only its own items. */
@RestController
@RequestMapping("/api/member/personal-events")
public class PersonalEventController {

    private final PersonalEventService service;

    public PersonalEventController(PersonalEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<View> month(@RequestParam YearMonth month, HttpServletRequest request) {
        return service.month(me(request).getId(), month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@RequestBody Request body, HttpServletRequest request) {
        return service.create(me(request).getId(), body);
    }

    @PutMapping("/{id}")
    public View update(@PathVariable Long id, @RequestBody Request body, HttpServletRequest request) {
        return service.update(me(request).getId(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(me(request).getId(), id);
    }

    private static AppUser me(HttpServletRequest request) {
        return UserSession.user(request);
    }
}
