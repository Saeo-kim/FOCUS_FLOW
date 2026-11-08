package com.example.project_flow.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface ProjectRecordRepository<E> extends JpaRepository<E, Long> {

    List<E> findByProject_Id(Long projectId, Sort sort);

    boolean existsByProject_Id(Long projectId);
}
