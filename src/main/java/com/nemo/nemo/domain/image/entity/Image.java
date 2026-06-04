package com.nemo.nemo.domain.image.entity;

import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private Member uploader;

    private String filePath;

    private String url;

    private long size;

    private String mimeType;

    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static Image create(Album album, Member uploader, String filePath, String url, long size, String mimeType) {
        Image image = new Image();
        image.album = album;
        image.uploader = uploader;
        image.filePath = filePath;
        image.url = url;
        image.size = size;
        image.mimeType = mimeType;
        return image;
    }
}
