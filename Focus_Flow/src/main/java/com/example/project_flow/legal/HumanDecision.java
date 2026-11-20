package com.example.project_flow.legal;

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
 * An append-only record of a final decision made by a person. Rows are never updated or deleted;
 * a later decision is a new row. {@code assistantState} keeps what the Decision Assistant said at that time.
 */
@Entity
@Table(name = "legal_human_decisions")
public class HumanDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HumanOutcome outcome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Column(nullable = false)
    private Long decidedByUserId;

    @Column(nullable = false, length = 60)
    private String decidedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DecisionState assistantState;

    @Column(nullable = false)
    private LocalDateTime decidedAt;

    protected HumanDecision() {
    }

    public HumanDecision(Long reviewId, HumanOutcome outcome, String reasoning, Long decidedByUserId, String decidedByName,
                         DecisionState assistantState, LocalDateTime decidedAt) {
        this.reviewId = reviewId;
        this.outcome = outcome;
        this.reasoning = reasoning;
        this.decidedByUserId = decidedByUserId;
        this.decidedByName = decidedByName;
        this.assistantState = assistantState;
        this.decidedAt = decidedAt;
    }

    public Long getId() { return id; }
    public Long getReviewId() { return reviewId; }
    public HumanOutcome getOutcome() { return outcome; }
    public String getReasoning() { return reasoning; }
    public Long getDecidedByUserId() { return decidedByUserId; }
    public String getDecidedByName() { return decidedByName; }
    public DecisionState getAssistantState() { return assistantState; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}
