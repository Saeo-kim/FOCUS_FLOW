package com.example.project_flow.repository;

import com.example.project_flow.entity.Deadline;

import java.time.LocalDate;
import java.util.List;

public interface DeadlineRepository extends ProjectRecordRepository<Deadline> {

    List<Deadline> findByDueDateBetween(LocalDate from, LocalDate to);
}
