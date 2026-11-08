package com.example.project_flow.repository;

import com.example.project_flow.entity.ProjectPhase;

import java.time.LocalDate;
import java.util.List;

public interface ProjectPhaseRepository extends ProjectRecordRepository<ProjectPhase> {

    List<ProjectPhase> findByStartDateBetweenOrEndDateBetween(LocalDate from, LocalDate to, LocalDate from2, LocalDate to2);
}
