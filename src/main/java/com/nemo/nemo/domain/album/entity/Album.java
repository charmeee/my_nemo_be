package com.nemo.nemo.domain.album.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "albums")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 30)
    private String name;

    private String coverImage;

    private UUID creatorId;

    @Column(nullable = false)
    private boolean isLocked = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Album create(String name, String coverImage, UUID creatorId) {
        Album album = new Album();
        album.name = name;
        album.coverImage = coverImage;
        album.creatorId = creatorId;
        album.isLocked = false;
        return album;
    }

    public void update(String name, String coverImage) {
        if (name != null) this.name = name;
        if (coverImage != null) this.coverImage = coverImage;
    }

    public void lock() {
        this.isLocked = true;
    }

    public void unlock() {
        this.isLocked = false;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
