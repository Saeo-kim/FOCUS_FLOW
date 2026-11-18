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
 * Permission for a person or AI seat to see information up to a level. Only the Owner creates grants.
 * Grants are never edited or deleted: a grant ends by revocation or expiry. External AI transmission
 * is not part of any grant.
 */
@Entity
@Table(name = "security_ai_access_grants")
public class AiAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 60)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityLevel maxLevel;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(nullable = false, length = 60)
    private String grantedByName;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    protected AiAccessGrant() {
    }

    public AiAccessGrant(Long actorUserId, String actorName, SecurityLevel maxLevel, String justification,
                         String grantedByName, LocalDateTime grantedAt, LocalDateTime expiresAt) {
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.maxLevel = maxLevel;
        this.justification = justification;
        this.grantedByName = grantedByName;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public SecurityLevel getMaxLevel() { return maxLevel; }
    public String getJustification() { return justification; }
    public String getGrantedByName() { return grantedByName; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
