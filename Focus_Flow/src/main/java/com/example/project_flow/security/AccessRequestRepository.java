package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findAllByOrderByIdDesc();
    List<AccessRequest> findByStatusOrderByIdAsc(AccessRequest.Status status);
    Optional<AccessRequest> findFirstByActorUserIdAndItemIdAndStatusOrderByIdAsc(Long actorUserId, Long itemId, AccessRequest.Status status);
    long countByStatus(AccessRequest.Status status);
}
