package com.example.project_flow.legal;

/**
 * The Decision Assistant's state for a review. It describes the review's readiness, not a decision.
 * Only {@link #APPROVAL_ELIGIBLE} makes approval possible, and even then a person must decide.
 */
public enum DecisionState {
    /** A HIGH-risk finding needs human review before anything else. */
    BLOCK,
    /** One or more of the five specialist reviews is missing (or none is recorded). */
    INCOMPLETE,
    /** No HIGH finding and no missing review, but a MEDIUM finding needs an answer. */
    CLARIFICATION_REQUIRED,
    /** All five reviews are complete and show only LOW or NONE. Eligibility only, never an approval. */
    APPROVAL_ELIGIBLE
}
