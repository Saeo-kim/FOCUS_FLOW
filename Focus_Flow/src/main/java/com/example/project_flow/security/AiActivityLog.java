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
 * One access decision, in a hash chain: each row's hash covers the previous row's hash, so editing or
 * removing any row breaks the chain. The database also refuses UPDATE and DELETE on this table
 * (see AuditLogGuard), so the application cannot rewrite history even with a bug.
 */
@Entity
@Table(name = "security_ai_activity_log")
public class AiActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long seq;

    @Column(nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 60)
    private String actorName;

    @Column(nullable = false, length = 10)
    private String actorKind;

    @Column(length = 60)
    private String requestedByName;

    private Long itemId;

    @Column(nullable = false, length = 200)
    private String itemTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityLevel itemLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Destination destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 300)
    private String findings;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AiActivityLog() {
    }

    public AiActivityLog(long seq, String prevHash, String hash, Long actorUserId, String actorName, String actorKind,
                         String requestedByName, Long itemId, String itemTitle, SecurityLevel itemLevel,
                         Destination destination, Decision decision, String reason, String findings, LocalDateTime createdAt) {
        this.seq = seq;
        this.prevHash = prevHash;
        this.hash = hash;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorKind = actorKind;
        this.requestedByName = requestedByName;
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.itemLevel = itemLevel;
        this.destination = destination;
        this.decision = decision;
        this.reason = reason;
        this.findings = findings;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSeq() { return seq; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public String getActorKind() { return actorKind; }
    public String getRequestedByName() { return requestedByName; }
    public Long getItemId() { return itemId; }
    public String getItemTitle() { return itemTitle; }
    public SecurityLevel getItemLevel() { return itemLevel; }
    public Destination getDestination() { return destination; }
    public Decision getDecision() { return decision; }
    public String getReason() { return reason; }
    public String getFindings() { return findings; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
