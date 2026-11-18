package com.example.project_flow.legal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * The Owner: the one person with final decision authority. Append-only: an appointment is revoked
 * by setting {@code revokedAt}, never deleted. At most one appointment is active at a time. Until
 * someone is explicitly appointed, the Owner position is unassigned. Being an administrator does
 * not make anyone the Owner.
 */
@Entity
@Table(name = "legal_owner_appointments")
public class LegalOwnerAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String userName;

    @Column(nullable = false)
    private Long appointedByUserId;

    @Column(nullable = false, length = 60)
    private String appointedByName;

    @Column(nullable = false)
    private LocalDateTime appointedAt;

    private LocalDateTime revokedAt;

    @Column(length = 60)
    private String revokedByName;

    protected LegalOwnerAppointment() {
    }

    public LegalOwnerAppointment(Long userId, String userName, Long appointedByUserId, String appointedByName, LocalDateTime now) {
        this.userId = userId;
        this.userName = userName;
        this.appointedByUserId = appointedByUserId;
        this.appointedByName = appointedByName;
        this.appointedAt = now;
    }

    public void revoke(String byName, LocalDateTime now) {
        this.revokedAt = now;
        this.revokedByName = byName;
    }

    public boolean isActive() { return revokedAt == null; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public Long getAppointedByUserId() { return appointedByUserId; }
    public String getAppointedByName() { return appointedByName; }
    public LocalDateTime getAppointedAt() { return appointedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevokedByName() { return revokedByName; }
}
