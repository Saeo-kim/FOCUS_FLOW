package com.example.project_flow.admin.submission;

/** SUBMITTED waits for review; a REJECTED submission can be resubmitted, which makes it SUBMITTED again. */
public enum SubmissionStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
