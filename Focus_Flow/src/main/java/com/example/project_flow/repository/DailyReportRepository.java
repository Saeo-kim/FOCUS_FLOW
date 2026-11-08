package com.example.project_flow.repository;

import com.example.project_flow.entity.DailyReport;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends ProjectRecordRepository<DailyReport> {

    Optional<DailyReport> findByProject_IdAndReportDate(Long projectId, LocalDate reportDate);
}
