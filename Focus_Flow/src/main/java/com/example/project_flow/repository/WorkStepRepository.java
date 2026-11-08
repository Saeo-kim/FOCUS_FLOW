package com.example.project_flow.repository;

import com.example.project_flow.entity.WorkStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkStepRepository extends JpaRepository<WorkStep, Long> {

    List<WorkStep> findByTask_IdOrderBySortOrderAscIdAsc(Long taskId);

    /** Rows of [taskId, total, completed]. */
    @Query("""
            select s.task.id, count(s),
                   coalesce(sum(case when s.status = com.example.project_flow.entity.WorkStatus.COMPLETED then 1 else 0 end), 0)
            from WorkStep s group by s.task.id
            """)
    List<Object[]> countByTask();

    @Modifying
    @Query("delete from WorkStep s where s.task.id = :taskId")
    void deleteAllOfTask(@Param("taskId") Long taskId);
}
