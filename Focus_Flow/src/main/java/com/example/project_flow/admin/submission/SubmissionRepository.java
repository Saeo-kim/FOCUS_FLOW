package com.example.project_flow.admin.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findAllByOrderBySubmittedAtDescIdDesc();

    List<Submission> findByStatusOrderBySubmittedAtAscIdAsc(SubmissionStatus status);
}
