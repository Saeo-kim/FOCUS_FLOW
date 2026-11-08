package com.example.project_flow.entity;

import com.example.project_flow.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Separates what an AI tool claimed or produced from what a person actually confirmed. */
@Entity
@Table(name = "ai_verifications")
public class AiVerification extends ProjectRecord {

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(length = 100)
    private String aiTool;

    @Column(columnDefinition = "TEXT")
    private String requestText;

    @Column(columnDefinition = "TEXT")
    private String changedFiles;

    @Column(columnDefinition = "TEXT")
    private String aiActions;

    @Column(columnDefinition = "TEXT")
    private String testItems;

    @Column(columnDefinition = "TEXT")
    private String testResult;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CheckStatus testStatus = CheckStatus.NOT_RUN;

    @Column(columnDefinition = "TEXT")
    private String buildResult;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CheckStatus buildStatus = CheckStatus.NOT_RUN;

    @Column(columnDefinition = "TEXT")
    private String humanReview;

    @Column(nullable = false)
    private boolean verified;

    @Column(columnDefinition = "TEXT")
    private String followUp;

    @Column(nullable = false)
    private boolean approved;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AiVerification() {
    }

    public AiVerification(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void describe(String title, Task task, String aiTool, String requestText, String changedFiles,
                         String aiActions, String testItems, String testResult, String buildResult, String followUp) {
        this.title = title;
        this.task = task;
        this.aiTool = aiTool;
        this.requestText = requestText;
        this.changedFiles = changedFiles;
        this.aiActions = aiActions;
        this.testItems = testItems;
        this.testResult = testResult;
        this.buildResult = buildResult;
        this.followUp = followUp;
    }

    /** AI output is never verified by default: both checks must pass and a person must sign off. */
    public void review(CheckStatus testStatus, CheckStatus buildStatus, String humanReview,
                       boolean verified, boolean approved) {
        if (verified && (testStatus != CheckStatus.PASSED || buildStatus != CheckStatus.PASSED)) {
            throw new ConflictException("Tests and the build must both pass before this can be verified.");
        }
        if (verified && (humanReview == null || humanReview.isBlank())) {
            throw new ConflictException("Record what you personally checked before marking this verified.");
        }
        if (approved && !verified) {
            throw new ConflictException("Verify this work before giving final approval.");
        }
        this.testStatus = testStatus;
        this.buildStatus = buildStatus;
        this.humanReview = humanReview;
        this.verified = verified;
        this.approved = approved;
    }

    public String getTitle() { return title; }
    public Task getTask() { return task; }
    public String getAiTool() { return aiTool; }
    public String getRequestText() { return requestText; }
    public String getChangedFiles() { return changedFiles; }
    public String getAiActions() { return aiActions; }
    public String getTestItems() { return testItems; }
    public String getTestResult() { return testResult; }
    public CheckStatus getTestStatus() { return testStatus; }
    public String getBuildResult() { return buildResult; }
    public CheckStatus getBuildStatus() { return buildStatus; }
    public String getHumanReview() { return humanReview; }
    public boolean isVerified() { return verified; }
    public String getFollowUp() { return followUp; }
    public boolean isApproved() { return approved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
