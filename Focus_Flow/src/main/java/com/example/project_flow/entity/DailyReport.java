package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * Only what a person has to write down. Completed tasks, tasks in progress and logged errors are
 * read from their own tables when a report is shown, so nothing here duplicates the Activity Log.
 */
@Entity
@Table(name = "daily_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_report_project_date", columnNames = {"project_id", "report_date"}))
public class DailyReport extends ProjectRecord {

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(columnDefinition = "TEXT")
    private String goal;

    @Column(columnDefinition = "TEXT")
    private String actualWork;

    @Column(columnDefinition = "TEXT")
    private String problemsSolved;

    @Column(columnDefinition = "TEXT")
    private String incompleteReason;

    @Column(columnDefinition = "TEXT")
    private String nextWork;

    @Column(columnDefinition = "TEXT")
    private String evidenceNote;

    public void update(LocalDate reportDate, String goal, String actualWork, String problemsSolved,
                       String incompleteReason, String nextWork, String evidenceNote) {
        this.reportDate = reportDate;
        this.goal = goal;
        this.actualWork = actualWork;
        this.problemsSolved = problemsSolved;
        this.incompleteReason = incompleteReason;
        this.nextWork = nextWork;
        this.evidenceNote = evidenceNote;
    }

    public LocalDate getReportDate() { return reportDate; }
    public String getGoal() { return goal; }
    public String getActualWork() { return actualWork; }
    public String getProblemsSolved() { return problemsSolved; }
    public String getIncompleteReason() { return incompleteReason; }
    public String getNextWork() { return nextWork; }
    public String getEvidenceNote() { return evidenceNote; }
}
