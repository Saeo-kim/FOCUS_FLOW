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

/** What one specialist function found in one review. At most one row per review and function. */
@Entity
@Table(name = "compliance_review_findings")
public class ComplianceFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reviewId;

    @Column(nullable = false, length = 60)
    private String functionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "TEXT")
    private String finding;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ComplianceFinding() {
    }

    public ComplianceFinding(Long reviewId, String functionKey, RiskLevel riskLevel, String finding, String recommendation, LocalDateTime now) {
        this.reviewId = reviewId;
        this.functionKey = functionKey;
        this.riskLevel = riskLevel;
        this.finding = finding;
        this.recommendation = recommendation;
        this.updatedAt = now;
    }

    public void update(RiskLevel riskLevel, String finding, String recommendation, LocalDateTime now) {
        this.riskLevel = riskLevel;
        this.finding = finding;
        this.recommendation = recommendation;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getReviewId() { return reviewId; }
    public String getFunctionKey() { return functionKey; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getFinding() { return finding; }
    public String getRecommendation() { return recommendation; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
