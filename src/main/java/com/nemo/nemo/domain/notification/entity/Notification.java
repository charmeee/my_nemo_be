package com.nemo.nemo.domain.notification.entity;

import com.nemo.nemo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Member user;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(columnDefinition = "jsonb")
    private String payload;

    private boolean isRead = false;

    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static Notification create(Member user, NotificationType type, String payload) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.payload = payload;
        notification.isRead = false;
        return notification;
    }

    public void markRead() {
        this.isRead = true;
    }
}
