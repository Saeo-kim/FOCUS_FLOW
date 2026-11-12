package com.example.project_flow.admin.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findBySubmission_IdOrderByVersionDescIdAsc(Long submissionId);

    /** Rows of {submissionId, fileCount, totalBytes}. */
    @Query("select f.submission.id, count(f), coalesce(sum(f.sizeBytes), 0) from SubmissionFile f group by f.submission.id")
    List<Object[]> totalsBySubmission();
}
