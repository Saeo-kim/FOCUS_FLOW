package com.example.project_flow.admin.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    @Query("select n from Notification n where n.recipientUserId is null or n.recipientUserId = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findVisible(@Param("userId") Long userId, Limit limit);

    @Query("select count(n) from Notification n where n.readAt is null and (n.recipientUserId is null or n.recipientUserId = :userId)")
    long countUnread(@Param("userId") Long userId);

    @Query("select n from Notification n where n.readAt is null and (n.recipientUserId is null or n.recipientUserId = :userId)")
    List<Notification> findUnread(@Param("userId") Long userId);
}
