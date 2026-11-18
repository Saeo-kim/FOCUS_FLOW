package com.example.project_flow.legal;

import com.example.project_flow.entity.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One document under legal review (agreement, terms, policy, contract...). It is the review
 * record for this function: title, supporting document reference, deadline, status, findings
 * and the recommended action. Nothing is ever deleted; a review is closed by its status.
 */
@Entity
@Table(name = "compliance_reviews")
public class ComplianceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String positionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReviewCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    /** A reference to the document: a link, file name or location. The text itself is not copied here. */
    @Column(length = 500)
    private String documentRef;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String findingsSummary;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    private Long createdByUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    protected ComplianceReview() {
    }

    public ComplianceReview(String positionKey, ReviewCategory category, String title, String documentRef, String description,
                            Priority priority, LocalDate deadline, Long createdByUserId, LocalDateTime now) {
        this.positionKey = positionKey;
        this.category = category;
        this.title = title;
        this.documentRef = documentRef;
        this.description = description;
        this.priority = priority;
        this.deadline = deadline;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(ReviewCategory category, String title, String documentRef, String description, Priority priority,
                       LocalDate deadline, String findingsSummary, String recommendedAction, LocalDateTime now) {
        this.category = category;
        this.title = title;
        this.documentRef = documentRef;
        this.description = description;
        this.priority = priority;
        this.deadline = deadline;
        this.findingsSummary = findingsSummary;
        this.recommendedAction = recommendedAction;
        this.updatedAt = now;
    }

    public void changeStatus(ReviewStatus next, LocalDateTime now) {
        this.status = next;
        this.updatedAt = now;
        this.completedAt = ReviewStatus.COMPLETED.contains(next) ? (completedAt == null ? now : completedAt) : null;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPositionKey() { return positionKey; }
    public ReviewCategory getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDocumentRef() { return documentRef; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public LocalDate getDeadline() { return deadline; }
    public ReviewStatus getStatus() { return status; }
    public String getFindingsSummary() { return findingsSummary; }
    public String getRecommendedAction() { return recommendedAction; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
