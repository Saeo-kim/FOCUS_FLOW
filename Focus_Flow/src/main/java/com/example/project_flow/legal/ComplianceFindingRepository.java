package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplianceFindingRepository extends JpaRepository<ComplianceFinding, Long> {
    List<ComplianceFinding> findByReviewIdOrderByIdAsc(Long reviewId);
    Optional<ComplianceFinding> findByReviewIdAndFunctionKey(Long reviewId, String functionKey);
    List<ComplianceFinding> findByReviewIdIn(Collection<Long> reviewIds);
}
