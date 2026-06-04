package com.nemo.nemo.domain.page.entity;

import com.nemo.nemo.domain.album.entity.Album;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "album_pages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlbumPage {

    @Id
    private UUID tlPageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    private String thumbnailUrl;

    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public static AlbumPage create(UUID tlPageId, Album album) {
        AlbumPage page = new AlbumPage();
        page.tlPageId = tlPageId;
        page.album = album;
        page.createdAt = LocalDateTime.now();
        return page;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
