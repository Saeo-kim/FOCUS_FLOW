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
 * A piece of company information with its classification. RESTRICTED items never store their content:
 * only a fingerprint (SHA-256), so the system cannot leak what it does not keep.
 */
@Entity
@Table(name = "security_classified_items")
public class ClassifiedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityLevel level;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 60)
    private String classifiedByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ClassifiedItem() {
    }

    public ClassifiedItem(String title, DataCategory category, SecurityLevel level, String content, String fingerprint,
                          String classifiedByName, LocalDateTime now) {
        this.title = title;
        this.category = category;
        this.level = level;
        this.content = content;
        this.fingerprint = fingerprint;
        this.classifiedByName = classifiedByName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reclassify(SecurityLevel next, String byName, LocalDateTime now) {
        this.level = next;
        this.classifiedByName = byName;
        this.updatedAt = now;
    }

    /** RESTRICTED content is never kept: only the fingerprint survives. */
    public void stripContentForRestricted() {
        this.content = null;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public DataCategory getCategory() { return category; }
    public SecurityLevel getLevel() { return level; }
    public String getContent() { return content; }
    public String getFingerprint() { return fingerprint; }
    public String getClassifiedByName() { return classifiedByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
