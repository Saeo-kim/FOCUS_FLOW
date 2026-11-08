package com.example.project_flow.entity;

import com.example.project_flow.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "requirements")
public class Requirement extends ProjectRecord {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 200)
    private String relatedFeature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsibility_id")
    private Responsibility responsibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private boolean tested;

    @Column(nullable = false)
    private boolean verified;

    @Column(columnDefinition = "TEXT")
    private String verificationResult;

    @Column(columnDefinition = "TEXT")
    private String evidenceNote;

    public void describe(String title, String detail, String relatedFeature,
                         Responsibility responsibility, Task task, String evidenceNote) {
        this.title = title;
        this.detail = detail;
        this.relatedFeature = relatedFeature;
        this.responsibility = responsibility;
        this.task = task;
        this.evidenceNote = evidenceNote;
    }

    /** Written code alone proves nothing: "verified" needs a recorded test and its outcome. */
    public void recordCheck(boolean completed, boolean tested, boolean verified, String verificationResult) {
        if (verified && !(completed && tested)) {
            throw new ConflictException("Mark a requirement completed and tested before verifying it.");
        }
        if (verified && (verificationResult == null || verificationResult.isBlank())) {
            throw new ConflictException("Describe the verification result before marking it verified.");
        }
        this.completed = completed;
        this.tested = tested;
        this.verified = verified;
        this.verificationResult = verificationResult;
    }

    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getRelatedFeature() { return relatedFeature; }
    public Responsibility getResponsibility() { return responsibility; }
    public Task getTask() { return task; }
    public boolean isCompleted() { return completed; }
    public boolean isTested() { return tested; }
    public boolean isVerified() { return verified; }
    public String getVerificationResult() { return verificationResult; }
    public String getEvidenceNote() { return evidenceNote; }
}
