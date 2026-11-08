package com.example.project_flow.repository;

import com.example.project_flow.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t left join fetch t.project order by t.createdAt desc, t.id desc")
    List<Task> findAllWithProject();

    @Query("""
            select t from Task t left join fetch t.project
            where t.dueDate between :from and :to or t.scheduledDate between :from and :to
            """)
    List<Task> findDatedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Rows of [projectId, total, completed]. */
    @Query("""
            select t.project.id, count(t), coalesce(sum(case when t.done = true then 1 else 0 end), 0)
            from Task t where t.project is not null group by t.project.id
            """)
    List<Object[]> countByProject();

    @Modifying
    @Query("update Task t set t.project = null where t.project.id = :projectId")
    void unassignProject(@Param("projectId") Long projectId);

    /** Rows of [responsibilityId, total, completed]. */
    @Query("""
            select t.responsibility.id, count(t), coalesce(sum(case when t.done = true then 1 else 0 end), 0)
            from Task t where t.responsibility is not null group by t.responsibility.id
            """)
    List<Object[]> countByResponsibility();

    @Query("""
            select t from Task t
            where t.project.id = :projectId and t.done = true and t.completedAt >= :from and t.completedAt < :to
            order by t.completedAt
            """)
    List<Task> findCompletedBetween(@Param("projectId") Long projectId, @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Query("""
            select t from Task t
            where t.project.id = :projectId and t.status = com.example.project_flow.entity.WorkStatus.IN_PROGRESS
            order by t.dueDate
            """)
    List<Task> findInProgress(@Param("projectId") Long projectId);

    @Modifying
    @Query("update Task t set t.responsibility = null where t.responsibility.id = :responsibilityId")
    void clearResponsibility(@Param("responsibilityId") Long responsibilityId);
}
