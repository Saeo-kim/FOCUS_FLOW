package com.example.project_flow.legal;

import java.util.List;

public enum ReviewStatus {
    OPEN,
    IN_REVIEW,
    NEEDS_CLARIFICATION,
    APPROVED,
    REJECTED,
    CLOSED;

    /** Reviews still waiting for a decision. */
    public static final List<ReviewStatus> PENDING = List.of(OPEN, IN_REVIEW, NEEDS_CLARIFICATION);
    /** Reviews with a final outcome. */
    public static final List<ReviewStatus> COMPLETED = List.of(APPROVED, REJECTED, CLOSED);
}
