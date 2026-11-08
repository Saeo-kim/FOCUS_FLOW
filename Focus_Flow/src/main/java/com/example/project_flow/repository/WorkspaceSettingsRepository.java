package com.example.project_flow.repository;

import com.example.project_flow.entity.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, Long> {
}
