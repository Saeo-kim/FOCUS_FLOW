package com.example.project_flow.legal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrgPositionFunctionRepository extends JpaRepository<OrgPositionFunction, Long> {
    List<OrgPositionFunction> findByPositionKeyOrderBySortOrderAsc(String positionKey);
    Optional<OrgPositionFunction> findByPositionKeyAndFunctionKey(String positionKey, String functionKey);
}
