package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single-row table: the application has one workspace and no user accounts. */
@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 60)
    private String displayName = "My Workspace";

    @Column(nullable = false)
    private int dailyTargetMinutes = 480;

    /** Set once the initial project data is created, so deleting that data never brings it back. */
    private Boolean starterProjectSeeded;

    public boolean isStarterProjectSeeded() {
        return Boolean.TRUE.equals(starterProjectSeeded);
    }

    public void markStarterProjectSeeded() {
        this.starterProjectSeeded = true;
    }

    /** Set once the JPA exam study plan is created, so deleting it never brings it back. */
    private Boolean studyPlanSeeded;

    public boolean isStudyPlanSeeded() {
        return Boolean.TRUE.equals(studyPlanSeeded);
    }

    public void markStudyPlanSeeded() {
        this.studyPlanSeeded = true;
    }

    public void update(String displayName, int dailyTargetMinutes) {
        this.displayName = displayName;
        this.dailyTargetMinutes = dailyTargetMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDailyTargetMinutes() {
        return dailyTargetMinutes;
    }
}
