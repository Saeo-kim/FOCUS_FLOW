package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplianceReviewRepository extends JpaRepository<ComplianceReview, Long> {
    List<ComplianceReview> findAllByOrderByIdDesc();
    List<ComplianceReview> findByStatusIn(Collection<ReviewStatus> statuses);
}
