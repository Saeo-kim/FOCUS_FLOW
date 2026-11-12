package com.example.project_flow.admin.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionEventRepository extends JpaRepository<SubmissionEvent, Long> {

    List<SubmissionEvent> findBySubmission_IdOrderByCreatedAtAscIdAsc(Long submissionId);

    List<SubmissionEvent> findTop12ByOrderByCreatedAtDescIdDesc();
}
