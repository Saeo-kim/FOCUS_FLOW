package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassifiedItemRepository extends JpaRepository<ClassifiedItem, Long> {
    List<ClassifiedItem> findAllByOrderByIdAsc();
}
