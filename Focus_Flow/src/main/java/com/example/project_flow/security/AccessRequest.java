package com.example.project_flow.security;

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
 * A high-risk access request waiting for the Owner. An approval covers exactly one use: it is consumed
 * when the access it approved is actually granted. Nobody can approve their own request.
 */
@Entity
@Table(name = "security_access_requests")
public class AccessRequest {

    public enum Status { PENDING, APPROVED, DENIED, CONSUMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 60)
    private String actorName;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 200)
    private String itemTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityLevel itemLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Destination destination;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(nullable = false)
    private Long requestedByUserId;

    @Column(nullable = false, length = 60)
    private String requestedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    private Long decidedByUserId;

    @Column(length = 60)
    private String decidedByName;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;

    private LocalDateTime consumedAt;

    protected AccessRequest() {
    }

    public AccessRequest(Long actorUserId, String actorName, Long itemId, String itemTitle, SecurityLevel itemLevel,
                         Destination destination, String purpose, Long requestedByUserId, String requestedByName,
                         LocalDateTime now) {
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.itemLevel = itemLevel;
        this.destination = destination;
        this.purpose = purpose;
        this.requestedByUserId = requestedByUserId;
        this.requestedByName = requestedByName;
        this.status = Status.PENDING;
        this.requestedAt = now;
    }

    public void decide(Status next, Long byId, String byName, String reason, LocalDateTime now) {
        this.status = next;
        this.decidedByUserId = byId;
        this.decidedByName = byName;
        this.decisionReason = reason;
        this.decidedAt = now;
    }

    public void consume(LocalDateTime now) {
        this.status = Status.CONSUMED;
        this.consumedAt = now;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public Long getItemId() { return itemId; }
    public String getItemTitle() { return itemTitle; }
    public SecurityLevel getItemLevel() { return itemLevel; }
    public Destination getDestination() { return destination; }
    public String getPurpose() { return purpose; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public String getRequestedByName() { return requestedByName; }
    public Status getStatus() { return status; }
    public Long getDecidedByUserId() { return decidedByUserId; }
    public String getDecidedByName() { return decidedByName; }
    public String getDecisionReason() { return decisionReason; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}
