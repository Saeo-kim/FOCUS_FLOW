package com.example.project_flow.personal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonalEventRepository extends JpaRepository<PersonalEvent, Long> {
    List<PersonalEvent> findByUserIdAndEventDateBetweenOrderByEventDateAscStartTimeAscIdAsc(Long userId, LocalDate from, LocalDate to);
    Optional<PersonalEvent> findByIdAndUserId(Long id, Long userId);
}
