package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAccessGrantRepository extends JpaRepository<AiAccessGrant, Long> {
    List<AiAccessGrant> findByActorUserIdOrderByIdDesc(Long actorUserId);
    List<AiAccessGrant> findAllByOrderByIdDesc();
}
