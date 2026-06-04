package com.nemo.nemo.domain.notification.repository;

import com.nemo.nemo.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentByUserId(@Param("userId") UUID userId, @Param("since") java.time.LocalDateTime since);

    long countByUserIdAndIsReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId")
    void markAllRead(@Param("userId") UUID userId);
}
