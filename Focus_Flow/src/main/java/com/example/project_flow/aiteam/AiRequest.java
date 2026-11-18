package com.example.project_flow.aiteam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One piece of work sent to an AI teammate, and what came back. The task it belongs to keeps
 * its own status; this row keeps the hand-off details. Only the report is stored, never the
 * other application's ledger. No foreign keys, so existing tables are untouched.
 */
@Entity
@Table(name = "ai_requests")
public class AiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String agentKey;

    /** Null until the other application chooses a kind (when none was asked for). */
    @Column(length = 40)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    /** The task this work belongs to. Plain id, no foreign key. */
    private Long taskId;

    /** The comment that asked for this work, when it came from a @CFO mention. */
    private Long commentId;

    @Column(nullable = false)
    private Long requestedByUserId;

    /** Idempotency key sent to the other application; a retry never creates a second item. */
    @Column(nullable = false, unique = true, length = 64)
    private String externalRef;

    /** The other application's id for this work, once it has accepted it. */
    private Long officeTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiRequestStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 300)
    private String lastError;

    @Column(length = 1000)
    private String summary;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String resultJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    protected AiRequest() {
    }

    public AiRequest(String agentKey, String kind, String title, String instructions, Long taskId, Long commentId,
                     Long requestedByUserId, LocalDateTime now) {
        this.agentKey = agentKey;
        this.kind = kind;
        this.title = title;
        this.instructions = instructions;
        this.taskId = taskId;
        this.commentId = commentId;
        this.requestedByUserId = requestedByUserId;
        this.externalRef = "ff-" + UUID.randomUUID().toString().replace("-", "");
        this.status = AiRequestStatus.QUEUED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void accepted(long officeTaskId, AiRequestStatus status, LocalDateTime now) {
        this.officeTaskId = officeTaskId;
        this.status = status;
        this.attempts++;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void sendFailed(String error, LocalDateTime now) {
        this.attempts++;
        this.lastError = clip(error);
        this.updatedAt = now;
    }

    public void progress(AiRequestStatus next, LocalDateTime now) {
        this.status = next;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void completed(String kind, String summary, String resultJson, LocalDateTime now) {
        this.status = AiRequestStatus.COMPLETED;
        if (kind != null) this.kind = kind;
        this.summary = clip(summary);
        this.resultJson = resultJson;
        this.lastError = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void failed(String error, LocalDateTime now) {
        this.status = AiRequestStatus.FAILED;
        this.lastError = clip(error);
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void noteError(String error, LocalDateTime now) {
        this.lastError = clip(error);
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return AiRequestStatus.OPEN.contains(status);
    }

    private static String clip(String value) {
        if (value == null) return null;
        return Objects.toString(value).length() > 300 ? value.substring(0, 300) : value;
    }

    public Long getId() { return id; }
    public String getAgentKey() { return agentKey; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getInstructions() { return instructions; }
    public Long getTaskId() { return taskId; }
    public Long getCommentId() { return commentId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getExternalRef() { return externalRef; }
    public Long getOfficeTaskId() { return officeTaskId; }
    public AiRequestStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getSummary() { return summary; }
    public String getResultJson() { return resultJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
