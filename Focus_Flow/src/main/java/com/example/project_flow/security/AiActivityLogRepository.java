package com.example.project_flow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Read and append only by design: no update or delete methods are used anywhere in the application. */
public interface AiActivityLogRepository extends JpaRepository<AiActivityLog, Long> {
    List<AiActivityLog> findAllByOrderBySeqAsc();
    List<AiActivityLog> findTop200ByOrderBySeqDesc();
    Optional<AiActivityLog> findFirstByOrderBySeqDesc();
}
