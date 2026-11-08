package com.example.project_flow.controller;

import com.example.project_flow.dto.ActivityResponse;
import com.example.project_flow.dto.CalendarEntryResponse;
import com.example.project_flow.dto.SettingsDto;
import com.example.project_flow.entity.WorkspaceSettings;
import com.example.project_flow.service.ActivityLogService;
import com.example.project_flow.service.CalendarService;
import com.example.project_flow.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkspaceController {

    private final CalendarService calendar;
    private final ActivityLogService activity;
    private final SettingsService settings;

    public WorkspaceController(CalendarService calendar, ActivityLogService activity, SettingsService settings) {
        this.calendar = calendar;
        this.activity = activity;
        this.settings = settings;
    }

    @GetMapping("/calendar")
    public List<CalendarEntryResponse> calendar(@RequestParam YearMonth month) {
        return calendar.month(month);
    }

    @GetMapping("/activity")
    public List<ActivityResponse> activity(@RequestParam(defaultValue = "100") int limit) {
        return activity.recent(limit);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        WorkspaceSettings current = settings.current();
        return new SettingsDto(current.getDisplayName(), current.getDailyTargetMinutes());
    }

    @PutMapping("/settings")
    public SettingsDto saveSettings(@Valid @RequestBody SettingsDto request) {
        return settings.save(request);
    }
}
