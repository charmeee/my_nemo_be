package com.nemo.nemo.domain.sync.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tldraw_documents")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TLDrawDocument {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID albumId;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private long serverClock;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static TLDrawDocument create(UUID albumId) {
        TLDrawDocument doc = new TLDrawDocument();
        doc.albumId = albumId;
        doc.state = "{}";
        doc.serverClock = 0;
        doc.updatedAt = LocalDateTime.now();
        return doc;
    }

    public void updateState(String state, long serverClock) {
        this.state = state;
        this.serverClock = serverClock;
    }
}
