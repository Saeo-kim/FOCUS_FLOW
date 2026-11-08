package com.example.project_flow.entity;

public enum DeadlineCategory {
    MILESTONE, DELIVERABLE, WEEKLY_REPORT, FINAL_REPORT, JAR, AI_LOG, DEPLOYMENT, PRESENTATION;

    /** Milestones that must stand out on the calendar. */
    public boolean isMajor() {
        return this == FINAL_REPORT || this == JAR || this == DEPLOYMENT || this == PRESENTATION;
    }
}
