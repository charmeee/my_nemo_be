package com.nemo.nemo.domain.excalidraw.entity;

import com.nemo.nemo.domain.album.entity.Album;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "excalidraw_pages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExcalidrawPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "page_id")
    private UUID pageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    private String name;

    private int pageOrder;

    @Column(columnDefinition = "TEXT")
    private String elements;

    private long serverClock;

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

    public static ExcalidrawPage create(Album album, String name, int pageOrder) {
        ExcalidrawPage page = new ExcalidrawPage();
        page.album = album;
        page.name = name;
        page.pageOrder = pageOrder;
        page.elements = "[]";
        page.serverClock = 0;
        return page;
    }

    public void updateElements(String elements, long serverClock) {
        this.elements = elements;
        this.serverClock = serverClock;
    }

    public void updateMeta(String name, int pageOrder) {
        this.name = name;
        this.pageOrder = pageOrder;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
