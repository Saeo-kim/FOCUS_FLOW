package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {
    List<SecurityIncident> findAllByOrderByIdDesc();
}
