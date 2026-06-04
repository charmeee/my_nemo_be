package com.nemo.nemo.domain.invite.entity;

import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invite_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @Column(unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    private AlbumRole role;

    private boolean approvalRequired = false;

    private boolean isActive = true;

    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static InviteLink create(Album album, AlbumRole role, boolean approvalRequired, String code) {
        InviteLink link = new InviteLink();
        link.album = album;
        link.role = role;
        link.approvalRequired = approvalRequired;
        link.code = code;
        link.isActive = true;
        return link;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
