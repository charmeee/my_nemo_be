package com.nemo.nemo.domain.album.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.dto.*;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.invite.entity.InviteLink;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.service.NotificationService;
import com.nemo.nemo.domain.sync.service.SessionGuard;
import com.nemo.nemo.domain.trash.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final MemberRepository memberRepository;
    @Lazy
    private final TrashService trashService;
    private final SessionGuard sessionGuard;
    @Lazy
    private final NotificationService notificationService;
    private final InviteLinkRepository inviteLinkRepository;

    public AlbumListResponse getMyAlbums(UUID userId) {
        List<Album> ownedAlbums = albumRepository.findByCreatorIdAndDeletedAtIsNull(userId);
        List<UUID> ownedIds = ownedAlbums.stream().map(Album::getId).toList();

        List<Album> joinedAlbums = albumRepository.findAlbumsByMemberId(userId).stream()
                .filter(a -> !ownedIds.contains(a.getId()))
                .toList();

        List<AlbumResponse> owned = ownedAlbums.stream()
                .map(a -> toResponse(a, userId))
                .toList();

        List<AlbumResponse> joined = joinedAlbums.stream()
                .map(a -> toResponse(a, userId))
                .toList();

        return new AlbumListResponse(owned, joined);
    }

    @Transactional
    public AlbumResponse createAlbum(UUID userId, AlbumCreateRequest req) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        Album album = Album.create(req.name(), req.coverImage(), userId);
        albumRepository.save(album);

        AlbumMember albumMember = AlbumMember.create(album, member, AlbumRole.ADMIN, MemberStatus.ACTIVE);
        albumMemberRepository.save(albumMember);

        // N-CORE-01: 앨범 생성 시 초대 링크 자동 발급 (EDITOR 역할, 승인 불필요)
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        inviteLinkRepository.save(InviteLink.create(album, AlbumRole.EDITOR, false, code));

        return toResponse(album, userId);
    }

    public AlbumResponse getAlbum(UUID albumId, UUID userId) {
        Album album = getAlbumOrThrow(albumId);
        getMemberOrThrow(albumId, userId);
        return toResponse(album, userId);
    }

    @Transactional
    public AlbumResponse updateAlbum(UUID albumId, UUID userId, AlbumUpdateRequest req) {
        Album album = getAlbumOrThrow(albumId);
        AlbumMember member = getMemberOrThrow(albumId, userId);
        requireAdmin(member);

        album.update(req.name(), req.coverImage());

        List<String> memberIds = albumMemberRepository
                .findByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE).stream()
                .map(am -> am.getUser().getId().toString())
                .toList();

        if (req.name() != null || req.coverImage() != null) {
            for (String memberId : memberIds) {
                notificationService.send(memberId, NotificationType.ALBUM_UPDATED,
                        Map.of("albumId", albumId.toString()));
            }
        }

        if (req.isLocked() != null) {
            if (req.isLocked()) {
                album.lock();
                sessionGuard.forceCloseAll(albumId.toString(), "album-locked");
                for (String memberId : memberIds) {
                    notificationService.send(memberId, NotificationType.ALBUM_LOCKED,
                            Map.of("albumId", albumId.toString()));
                }
            } else {
                album.unlock();
                for (String memberId : memberIds) {
                    notificationService.send(memberId, NotificationType.ALBUM_UNLOCKED,
                            Map.of("albumId", albumId.toString()));
                }
            }
        }

        return toResponse(album, userId);
    }

    @Transactional
    public void deleteAlbum(UUID albumId, UUID userId) {
        Album album = getAlbumOrThrow(albumId);
        AlbumMember member = getMemberOrThrow(albumId, userId);
        requireAdmin(member);

        album.softDelete();
        trashService.addAlbumToTrash(albumId, userId);
    }

    private Album getAlbumOrThrow(UUID albumId) {
        return albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));
    }

    private AlbumMember getMemberOrThrow(UUID albumId, UUID userId) {
        return albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));
    }

    private void requireAdmin(AlbumMember member) {
        if (member.getRole() != AlbumRole.ADMIN) {
            throw new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED);
        }
    }

    private AlbumResponse toResponse(Album album, UUID userId) {
        long memberCount = albumMemberRepository.countByAlbumIdAndStatus(album.getId(), MemberStatus.ACTIVE);

        String myRole = albumMemberRepository.findActiveByAlbumIdAndUserId(album.getId(), userId)
                .map(am -> am.getRole().name())
                .orElse(null);

        return new AlbumResponse(
                album.getId().toString(),
                album.getName(),
                album.getCoverImage(),
                album.isLocked(),
                album.getCreatedAt() != null ? album.getCreatedAt().toString() : null,
                album.getUpdatedAt() != null ? album.getUpdatedAt().toString() : null,
                (int) memberCount,
                myRole
        );
    }
}
