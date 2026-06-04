package com.nemo.nemo.domain.trash.entity;

import com.nemo.nemo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trash")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trash {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TrashType type;

    private UUID referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private Member deletedBy;

    @Column(columnDefinition = "jsonb")
    private String originalData;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static Trash create(TrashType type, UUID referenceId, Member deletedBy, String originalData, int retentionDays) {
        Trash trash = new Trash();
        trash.type = type;
        trash.referenceId = referenceId;
        trash.deletedBy = deletedBy;
        trash.originalData = originalData;
        trash.expiresAt = LocalDateTime.now().plusDays(retentionDays);
        return trash;
    }
}
