package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrgPositionRepository extends JpaRepository<OrgPosition, Long> {
    Optional<OrgPosition> findByPositionKey(String positionKey);
}
