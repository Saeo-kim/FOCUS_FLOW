package com.example.project_flow.aiteam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {

    List<AiRequest> findTop100ByAgentKeyOrderByIdDesc(String agentKey);

    List<AiRequest> findByStatusIn(Collection<AiRequestStatus> statuses);

    List<AiRequest> findByTaskIdOrderByIdAsc(Long taskId);

    boolean existsByTaskIdAndStatusIn(Long taskId, Collection<AiRequestStatus> statuses);
}
