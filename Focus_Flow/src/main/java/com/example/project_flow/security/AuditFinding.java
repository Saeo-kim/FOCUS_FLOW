package com.example.project_flow.security;

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
 * A finding from an independent audit. The evidence is written once and fingerprinted; the application
 * has no operation that changes it, and the database refuses changes to it (see AuditLogGuard). Status
 * changes are recorded by who made them. The subject of an audit can never close its own finding.
 */
@Entity
@Table(name = "security_audit_findings")
public class AuditFinding {

    public enum Source { IT_SECURITY_AUDITOR, EXTERNAL_SECURITY_AUDITOR }
    public enum Status { OPEN, IN_REMEDIATION, VERIFIED_CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(nullable = false, length = 120)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SecurityIncident.Severity severity;

    @Column(nullable = false, length = 80)
    private String responsibleDepartment;

    @Column(nullable = false, length = 120)
    private String responsibleRole;

    @Column(nullable = false)
    private LocalDate discoveredOn;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(nullable = false, length = 64)
    private String evidenceHash;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private boolean escalatedToOwner;

    private LocalDateTime ownerAcknowledgedAt;

    @Column(length = 60)
    private String ownerAcknowledgedByName;

    @Column(nullable = false, length = 60)
    private String auditorName;

    @Column(nullable = false)
    private Long auditorUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 60)
    private String lastStatusByName;

    protected AuditFinding() {
    }

    public AuditFinding(String title, Source source, String subject, SecurityIncident.Severity severity,
                        String responsibleDepartment, String responsibleRole, LocalDate discoveredOn, String evidence,
                        String evidenceHash, String recommendedAction, Long auditorUserId, String auditorName,
                        LocalDateTime now) {
        this.title = title;
        this.source = source;
        this.subject = subject;
        this.severity = severity;
        this.responsibleDepartment = responsibleDepartment;
        this.responsibleRole = responsibleRole;
        this.discoveredOn = discoveredOn;
        this.evidence = evidence;
        this.evidenceHash = evidenceHash;
        this.recommendedAction = recommendedAction;
        this.auditorUserId = auditorUserId;
        this.auditorName = auditorName;
        this.escalatedToOwner = severity == SecurityIncident.Severity.CRITICAL || severity == SecurityIncident.Severity.HIGH;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeStatus(Status next, String byName, LocalDateTime now) {
        this.status = next;
        this.lastStatusByName = byName;
        this.updatedAt = now;
    }

    public void acknowledgeByOwner(String byName, LocalDateTime now) {
        this.ownerAcknowledgedAt = now;
        this.ownerAcknowledgedByName = byName;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Source getSource() { return source; }
    public String getSubject() { return subject; }
    public SecurityIncident.Severity getSeverity() { return severity; }
    public String getResponsibleDepartment() { return responsibleDepartment; }
    public String getResponsibleRole() { return responsibleRole; }
    public LocalDate getDiscoveredOn() { return discoveredOn; }
    public String getEvidence() { return evidence; }
    public String getEvidenceHash() { return evidenceHash; }
    public String getRecommendedAction() { return recommendedAction; }
    public Status getStatus() { return status; }
    public boolean isEscalatedToOwner() { return escalatedToOwner; }
    public LocalDateTime getOwnerAcknowledgedAt() { return ownerAcknowledgedAt; }
    public String getOwnerAcknowledgedByName() { return ownerAcknowledgedByName; }
    public String getAuditorName() { return auditorName; }
    public Long getAuditorUserId() { return auditorUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getLastStatusByName() { return lastStatusByName; }
}
