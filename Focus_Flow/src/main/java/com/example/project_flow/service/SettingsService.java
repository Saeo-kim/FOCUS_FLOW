package com.example.project_flow.service;

import com.example.project_flow.dto.SettingsDto;
import com.example.project_flow.entity.WorkspaceSettings;
import com.example.project_flow.repository.WorkspaceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final WorkspaceSettingsRepository repository;
    private final ActivityLogService activity;

    public SettingsService(WorkspaceSettingsRepository repository, ActivityLogService activity) {
        this.repository = repository;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public WorkspaceSettings current() {
        return repository.findById(WorkspaceSettings.SINGLETON_ID).orElseGet(WorkspaceSettings::new);
    }

    @Transactional
    public SettingsDto save(SettingsDto request) {
        WorkspaceSettings settings = current();
        settings.update(request.displayName().trim(), request.dailyTargetMinutes());
        repository.save(settings);
        activity.record("SETTINGS_UPDATED", "Updated workspace settings");
        return new SettingsDto(settings.getDisplayName(), settings.getDailyTargetMinutes());
    }
}
