package com.example.project_flow.admin.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Work handed in for one schedule phase (phase). Project, phase and task are stored as plain ids,
 * not foreign keys, on purpose: the existing tables gain no constraints, so deleting a phase or
 * task in FOCUS FLOW works exactly as before and the submission simply shows the link as removed.
 */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long phaseId;

    private Long taskId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, length = 80)
    private String submitterName;

    /** Set when the submitter has an account; kept nullable for submissions made on someone's behalf. */
    private Long submitterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(nullable = false)
    private int currentVersion = 1;

    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    @Column(length = 80)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Submission() {
    }

    public Submission(Long projectId, Long phaseId, Long taskId, String title, String note,
                      String submitterName, Long submitterUserId, LocalDateTime now) {
        this.projectId = projectId;
        this.phaseId = phaseId;
        this.taskId = taskId;
        this.title = title;
        this.note = note;
        this.submitterName = submitterName;
        this.submitterUserId = submitterUserId;
        this.submittedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(Long taskId, String title, String note, LocalDateTime now) {
        this.taskId = taskId;
        this.title = title;
        this.note = note;
        this.updatedAt = now;
    }

    public void resubmit(String note, LocalDateTime now) {
        this.currentVersion++;
        if (note != null) {
            this.note = note;
        }
        this.status = SubmissionStatus.SUBMITTED;
        clearReview();
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public void review(SubmissionStatus decision, String comment, String reviewer, LocalDateTime now) {
        this.status = decision;
        this.reviewComment = comment;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void reopen(LocalDateTime now) {
        this.status = SubmissionStatus.SUBMITTED;
        clearReview();
        this.updatedAt = now;
    }

    private void clearReview() {
        this.reviewComment = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getPhaseId() { return phaseId; }
    public Long getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public String getSubmitterName() { return submitterName; }
    public Long getSubmitterUserId() { return submitterUserId; }
    public SubmissionStatus getStatus() { return status; }
    public int getCurrentVersion() { return currentVersion; }
    public String getReviewComment() { return reviewComment; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
