package com.example.project_flow.admin.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * A null recipient means "every administrator". {@code admin} decides whether those broadcast
 * notices are visible, so a member only ever sees notices addressed to them.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    @Query("select n from Notification n where (n.recipientUserId = :userId or (:admin = true and n.recipientUserId is null)) order by n.createdAt desc, n.id desc")
    List<Notification> findVisible(@Param("userId") Long userId, @Param("admin") boolean admin, Limit limit);

    @Query("select count(n) from Notification n where n.readAt is null and (n.recipientUserId = :userId or (:admin = true and n.recipientUserId is null))")
    long countUnread(@Param("userId") Long userId, @Param("admin") boolean admin);

    @Query("select n from Notification n where n.readAt is null and (n.recipientUserId = :userId or (:admin = true and n.recipientUserId is null))")
    List<Notification> findUnread(@Param("userId") Long userId, @Param("admin") boolean admin);
}
