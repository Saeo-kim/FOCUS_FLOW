package com.example.project_flow.repository;

import com.example.project_flow.entity.ErrorLog;

import java.time.LocalDate;
import java.util.List;

public interface ErrorLogRepository extends ProjectRecordRepository<ErrorLog> {

    List<ErrorLog> findByProject_IdAndOccurredOn(Long projectId, LocalDate occurredOn);
}
