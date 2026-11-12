package com.example.project_flow.admin.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    public record EditRequest(
            @NotBlank(message = "Enter a title.") @Size(max = 200, message = "Keep the title under 200 characters.") String title,
            @Size(max = 5000, message = "Keep the note under 5000 characters.") String note,
            Long taskId) {
    }

    public record ReviewRequest(@Size(max = 2000, message = "Keep the comment under 2000 characters.") String comment) {
    }

    /** Names are resolved from the existing tables; null means the linked record was deleted. */
    public record SubmissionResponse(
            Long id,
            Long projectId, String projectName,
            Long phaseId, String phaseName, LocalDate phaseStartDate, LocalDate phaseEndDate,
            Long taskId, String taskTitle,
            String title, String note, String submitterName, Long submitterUserId,
            SubmissionStatus status, int currentVersion,
            String reviewComment, String reviewedBy, LocalDateTime reviewedAt,
            LocalDateTime submittedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            long fileCount, long totalBytes,
            boolean late) {
    }

    public record FileResponse(Long id, int version, String originalName, String contentType, long sizeBytes,
                               LocalDateTime uploadedAt) {
        static FileResponse from(SubmissionFile f) {
            return new FileResponse(f.getId(), f.getVersion(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(), f.getUploadedAt());
        }
    }

    public record EventResponse(Long id, Long submissionId, String submissionTitle, SubmissionEvent.Type type,
                                int version, String comment, String actor, LocalDateTime createdAt) {
        static EventResponse from(SubmissionEvent e) {
            return new EventResponse(e.getId(), e.getSubmission().getId(), e.getSubmission().getTitle(), e.getType(),
                    e.getVersion(), e.getComment(), e.getActor(), e.getCreatedAt());
        }
    }

    public record SubmissionDetail(SubmissionResponse submission, List<FileResponse> files, List<EventResponse> events) {
    }

    /** One row of the 회차 board: what has been handed in for a phase and where it stands. */
    public record PhaseBoardRow(Long phaseId, Long projectId, String projectName, String phaseName,
                                LocalDate startDate, LocalDate endDate,
                                int total, int pending, int approved, int rejected,
                                SubmissionStatus overallStatus) {
    }

    public record DashboardSummary(long total, long pending, long approved, long rejected,
                                   long fileCount, long totalBytes, String storageFolder,
                                   List<SubmissionResponse> reviewQueue,
                                   List<EventResponse> recentEvents,
                                   List<PhaseBoardRow> phases) {
    }
}
