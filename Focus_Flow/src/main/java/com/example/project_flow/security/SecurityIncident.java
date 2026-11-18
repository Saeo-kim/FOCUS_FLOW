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

/** A security incident. Critical and high incidents are escalated to the Owner when they are recorded. */
@Entity
@Table(name = "security_incidents")
public class SecurityIncident {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { OPEN, IN_PROGRESS, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false, length = 80)
    private String responsibleDepartment;

    @Column(nullable = false, length = 120)
    private String responsibleRole;

    @Column(nullable = false)
    private LocalDate discoveredOn;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private boolean escalatedToOwner;

    private LocalDateTime ownerAcknowledgedAt;

    @Column(length = 60)
    private String ownerAcknowledgedByName;

    @Column(nullable = false, length = 60)
    private String recordedByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    protected SecurityIncident() {
    }

    public SecurityIncident(String title, Severity severity, String responsibleDepartment, String responsibleRole,
                            LocalDate discoveredOn, String evidence, String recommendedAction, String recordedByName,
                            LocalDateTime now) {
        this.title = title;
        this.severity = severity;
        this.responsibleDepartment = responsibleDepartment;
        this.responsibleRole = responsibleRole;
        this.discoveredOn = discoveredOn;
        this.evidence = evidence;
        this.recommendedAction = recommendedAction;
        this.recordedByName = recordedByName;
        this.escalatedToOwner = severity == Severity.CRITICAL || severity == Severity.HIGH;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeStatus(Status next, LocalDateTime now) {
        this.status = next;
        this.updatedAt = now;
        this.resolvedAt = next == Status.RESOLVED ? (resolvedAt == null ? now : resolvedAt) : null;
    }

    public void acknowledgeByOwner(String byName, LocalDateTime now) {
        this.ownerAcknowledgedAt = now;
        this.ownerAcknowledgedByName = byName;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Severity getSeverity() { return severity; }
    public String getResponsibleDepartment() { return responsibleDepartment; }
    public String getResponsibleRole() { return responsibleRole; }
    public LocalDate getDiscoveredOn() { return discoveredOn; }
    public String getEvidence() { return evidence; }
    public String getRecommendedAction() { return recommendedAction; }
    public Status getStatus() { return status; }
    public boolean isEscalatedToOwner() { return escalatedToOwner; }
    public LocalDateTime getOwnerAcknowledgedAt() { return ownerAcknowledgedAt; }
    public String getOwnerAcknowledgedByName() { return ownerAcknowledgedByName; }
    public String getRecordedByName() { return recordedByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
