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
 * A security policy version. A policy is written by the security function (CISO or Owner) and takes
 * effect only when the Owner approves it. The author cannot approve their own policy. Approving a new
 * version supersedes the previous one; no version is deleted.
 */
@Entity
@Table(name = "security_policies")
public class SecurityPolicy {

    public enum Status { DRAFT, APPROVED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String policyKey;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private Long authoredByUserId;

    @Column(nullable = false, length = 60)
    private String authoredByName;

    private Long approvedByUserId;

    @Column(length = 60)
    private String approvedByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    protected SecurityPolicy() {
    }

    public SecurityPolicy(String policyKey, int version, String title, String body, Long authoredByUserId,
                          String authoredByName, LocalDateTime now) {
        this.policyKey = policyKey;
        this.version = version;
        this.title = title;
        this.body = body;
        this.authoredByUserId = authoredByUserId;
        this.authoredByName = authoredByName;
        this.createdAt = now;
    }

    public void approve(Long byId, String byName, LocalDateTime now) {
        this.status = Status.APPROVED;
        this.approvedByUserId = byId;
        this.approvedByName = byName;
        this.approvedAt = now;
    }

    public void supersede() {
        this.status = Status.SUPERSEDED;
    }

    public Long getId() { return id; }
    public String getPolicyKey() { return policyKey; }
    public int getVersion() { return version; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public Long getAuthoredByUserId() { return authoredByUserId; }
    public String getAuthoredByName() { return authoredByName; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public String getApprovedByName() { return approvedByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
}
