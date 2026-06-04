package com.nemo.nemo.domain.album.entity;

import com.nemo.nemo.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "album_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlbumMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Member user;

    @Enumerated(EnumType.STRING)
    private AlbumRole role;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    private LocalDateTime joinedAt;

    public static AlbumMember create(Album album, Member user, AlbumRole role, MemberStatus status) {
        AlbumMember member = new AlbumMember();
        member.album = album;
        member.user = user;
        member.role = role;
        member.status = status;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    public void approve() {
        this.status = MemberStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = MemberStatus.REJECTED;
    }

    public void changeRole(AlbumRole role) {
        this.role = role;
    }
}
