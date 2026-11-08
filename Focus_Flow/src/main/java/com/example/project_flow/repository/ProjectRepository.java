package com.example.project_flow.repository;

import com.example.project_flow.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findByDueDateBetween(LocalDate from, LocalDate to);
}
