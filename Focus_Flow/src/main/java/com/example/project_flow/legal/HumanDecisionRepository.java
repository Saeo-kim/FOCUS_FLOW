package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HumanDecisionRepository extends JpaRepository<HumanDecision, Long> {
    List<HumanDecision> findByReviewIdOrderByIdAsc(Long reviewId);
}
