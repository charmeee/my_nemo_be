package com.nemo.nemo.domain.album.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.dto.MemberResponse;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.service.NotificationService;
import com.nemo.nemo.domain.sync.service.SessionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AlbumMemberService {

    private final AlbumMemberRepository albumMemberRepository;
    private final MemberRepository memberRepository;
    private final com.nemo.nemo.domain.album.repository.AlbumRepository albumRepository;
    private final SessionGuard sessionGuard;
    @Lazy
    private final NotificationService notificationService;
    @Lazy
    private final com.nemo.nemo.domain.trash.service.TrashService trashService;

    public List<MemberResponse> getMembers(UUID albumId, UUID requesterId) {
        getMemberOrThrow(albumId, requesterId);
        return albumMemberRepository.findByAlbumIdAndStatusOrderByJoinedAt(albumId, MemberStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    public List<MemberResponse> getPendingMembers(UUID albumId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        return albumMemberRepository.findByAlbumIdAndStatus(albumId, MemberStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void approveMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        AlbumMember target = getPendingMemberOrThrow(albumId, targetUserId);
        target.approve();
        notificationService.send(targetUserId.toString(), NotificationType.JOIN_APPROVED,
                Map.of("albumId", albumId.toString()));
    }

    @Transactional
    public void rejectMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        AlbumMember target = getPendingMemberOrThrow(albumId, targetUserId);
        target.reject();
        notificationService.send(targetUserId.toString(), NotificationType.JOIN_REJECTED,
                Map.of("albumId", albumId.toString()));
    }

    @Transactional
    public void changeRole(UUID albumId, UUID targetUserId, AlbumRole newRole, UUID requesterId) {
        requireAdmin(albumId, requesterId);

        if (requesterId.equals(targetUserId)) {
            throw new NemoException(ErrorCode.CANNOT_KICK_SELF);
        }
        if (newRole == AlbumRole.ADMIN) {
            throw new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED);
        }

        AlbumMember target = getMemberOrThrow(albumId, targetUserId);
        AlbumRole oldRole = target.getRole();
        target.changeRole(newRole);

        notificationService.send(targetUserId.toString(), NotificationType.ROLE_CHANGED,
                Map.of("albumId", albumId.toString(), "newRole", newRole.name()));

        if (oldRole != AlbumRole.VIEWER && newRole == AlbumRole.VIEWER) {
            sessionGuard.forceClose(albumId.toString(), targetUserId.toString(), "role-downgraded");
        }
    }

    @Transactional
    public void kickMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);

        if (requesterId.equals(targetUserId)) {
            throw new NemoException(ErrorCode.CANNOT_KICK_SELF);
        }

        AlbumMember target = getMemberOrThrow(albumId, targetUserId);
        sessionGuard.forceClose(albumId.toString(), targetUserId.toString(), "kicked");
        albumMemberRepository.delete(target);

        if (albumMemberRepository.countByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE) == 0) {
            albumRepository.findById(albumId).ifPresent(album -> {
                album.softDelete();
                trashService.addAlbumToTrash(albumId, requesterId);
            });
        }
    }

    @Transactional
    public void leaveAlbum(UUID albumId, UUID userId) {
        AlbumMember member = getMemberOrThrow(albumId, userId);

        if (member.getRole() == AlbumRole.ADMIN) {
            throw new NemoException(ErrorCode.ADMIN_MUST_TRANSFER);
        }

        String adminId = albumMemberRepository.findByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE).stream()
                .filter(am -> am.getRole() == AlbumRole.ADMIN)
                .map(am -> am.getUser().getId().toString())
                .findFirst().orElse(null);

        albumMemberRepository.delete(member);

        if (adminId != null) {
            notificationService.send(adminId, NotificationType.MEMBER_LEFT,
                    Map.of("albumId", albumId.toString(), "userId", userId.toString()));
        }

        if (albumMemberRepository.countByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE) == 0) {
            albumRepository.findById(albumId).ifPresent(album -> {
                album.softDelete();
                trashService.addAlbumToTrash(albumId, userId);
            });
        }
    }

    @Transactional
    public void transferAdmin(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);

        AlbumMember target = getMemberOrThrow(albumId, targetUserId);
        target.changeRole(AlbumRole.ADMIN);

        AlbumMember requester = getMemberOrThrow(albumId, requesterId);
        requester.changeRole(AlbumRole.EDITOR);
    }

    private AlbumMember getMemberOrThrow(UUID albumId, UUID userId) {
        return albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_IN_ALBUM));
    }

    private AlbumMember getPendingMemberOrThrow(UUID albumId, UUID userId) {
        return albumMemberRepository.findByAlbumIdAndUserId(albumId, userId)
                .filter(am -> am.getStatus() == MemberStatus.PENDING)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_IN_ALBUM));
    }

    private void requireAdmin(UUID albumId, UUID userId) {
        AlbumMember member = getMemberOrThrow(albumId, userId);
        if (member.getRole() != AlbumRole.ADMIN) {
            throw new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED);
        }
    }

    private MemberResponse toResponse(AlbumMember am) {
        return new MemberResponse(
                am.getId().toString(),
                am.getUser().getId().toString(),
                am.getUser().getNickname(),
                am.getUser().getProfileImage(),
                am.getRole().name(),
                am.getStatus().name(),
                am.getJoinedAt() != null ? am.getJoinedAt().toString() : null
        );
    }
}
