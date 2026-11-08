package com.example.project_flow.repository;

import com.example.project_flow.entity.Responsibility;

import java.time.LocalDate;
import java.util.List;

public interface ResponsibilityRepository extends ProjectRecordRepository<Responsibility> {

    List<Responsibility> findByDueDateBetween(LocalDate from, LocalDate to);
}
