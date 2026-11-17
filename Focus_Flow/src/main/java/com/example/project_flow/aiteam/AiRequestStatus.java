package com.example.project_flow.aiteam;

import java.util.List;

/**
 * QUEUED: saved here, not yet accepted by the other application (retried by the sync job).
 * SENT: accepted and waiting. IN_PROGRESS: being worked on. COMPLETED and FAILED are final.
 */
public enum AiRequestStatus {
    QUEUED,
    SENT,
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    /** Requests that still need attention from the sync job. */
    public static final List<AiRequestStatus> OPEN = List.of(QUEUED, SENT, IN_PROGRESS);
}
