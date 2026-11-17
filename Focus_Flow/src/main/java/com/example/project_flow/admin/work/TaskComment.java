package com.example.project_flow.admin.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A comment on a task. Written by a person, or by an AI teammate ({@code fromAgent}) when it
 * reports back. A comment that mentions @CFO also starts a request, linked by {@code aiRequestId}.
 */
@Entity
@Table(name = "task_comments")
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 60)
    private String authorName;

    @Column(nullable = false)
    private boolean fromAgent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private Long aiRequestId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected TaskComment() {
    }

    public TaskComment(Long taskId, Long authorUserId, String authorName, boolean fromAgent, String body,
                       Long aiRequestId, LocalDateTime createdAt) {
        this.taskId = taskId;
        this.authorUserId = authorUserId;
        this.authorName = authorName;
        this.fromAgent = fromAgent;
        this.body = body;
        this.aiRequestId = aiRequestId;
        this.createdAt = createdAt;
    }

    public void linkRequest(Long requestId) {
        this.aiRequestId = requestId;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getAuthorName() { return authorName; }
    public boolean isFromAgent() { return fromAgent; }
    public String getBody() { return body; }
    public Long getAiRequestId() { return aiRequestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
