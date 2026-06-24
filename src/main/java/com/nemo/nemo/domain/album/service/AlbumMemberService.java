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
    private final MemberRoleCacheService roleCacheService;

    // ACTIVE 멤버 목록 조회 (요청자 멤버십 검증)
    public List<MemberResponse> getMembers(UUID albumId, UUID requesterId) {
        getMemberOrThrow(albumId, requesterId);
        return albumMemberRepository.findByAlbumIdAndStatusOrderByJoinedAt(albumId, MemberStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    // 가입 대기(PENDING) 멤버 목록 조회 (ADMIN 전용)
    public List<MemberResponse> getPendingMembers(UUID albumId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        return albumMemberRepository.findByAlbumIdAndStatus(albumId, MemberStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    // 가입 승인 후 역할 캐시 무효화 + 알림 전송
    @Transactional
    public void approveMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        AlbumMember target = getPendingMemberOrThrow(albumId, targetUserId);
        target.approve();
        roleCacheService.invalidate(albumId.toString(), targetUserId.toString());
        notificationService.send(targetUserId.toString(), NotificationType.JOIN_APPROVED,
                Map.of("albumId", albumId.toString()));
    }

    // 가입 거절 후 캐시 무효화 + 알림 전송
    @Transactional
    public void rejectMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);
        AlbumMember target = getPendingMemberOrThrow(albumId, targetUserId);
        target.reject();
        roleCacheService.invalidate(albumId.toString(), targetUserId.toString());
        notificationService.send(targetUserId.toString(), NotificationType.JOIN_REJECTED,
                Map.of("albumId", albumId.toString()));
    }

    // 멤버 역할 변경: ADMIN 승격 불가, VIEWER 강등 시 활성 세션 강제 종료
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
        roleCacheService.invalidate(albumId.toString(), targetUserId.toString());

        notificationService.send(targetUserId.toString(), NotificationType.ROLE_CHANGED,
                Map.of("albumId", albumId.toString(), "newRole", newRole.name()));

        if (oldRole != AlbumRole.VIEWER && newRole == AlbumRole.VIEWER) {
            sessionGuard.forceClose(albumId.toString(), targetUserId.toString(), "role-downgraded");
        }
    }

    // 멤버 강제 추방: 세션 종료 후 삭제, 마지막 멤버면 앨범도 휴지통 이동
    @Transactional
    public void kickMember(UUID albumId, UUID targetUserId, UUID requesterId) {
        requireAdmin(albumId, requesterId);

        if (requesterId.equals(targetUserId)) {
            throw new NemoException(ErrorCode.CANNOT_KICK_SELF);
        }

        AlbumMember target = getMemberOrThrow(albumId, targetUserId);
        sessionGuard.forceClose(albumId.toString(), targetUserId.toString(), "kicked");
        albumMemberRepository.delete(target);
        roleCacheService.invalidate(albumId.toString(), targetUserId.toString());

        if (albumMemberRepository.countByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE) == 0) {
            albumRepository.findById(albumId).ifPresent(album -> {
                album.softDelete();
                trashService.addAlbumToTrash(albumId, requesterId);
            });
        }
    }

    // 본인 탈퇴: ADMIN은 위임 후에만 가능, 마지막 멤버면 앨범 휴지통 이동
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
        roleCacheService.invalidate(albumId.toString(), userId.toString());

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

    // 이메일로 직접 초대: PENDING 상태로 추가 후 알림 전송
    @Transactional
    public void inviteByEmail(UUID albumId, String email, AlbumRole role, UUID requesterId) {
        requireAdmin(albumId, requesterId);

        com.nemo.nemo.domain.member.entity.Member target = memberRepository.findByEmail(email)
                .orElseThrow(() -> new com.nemo.nemo.common.exception.NemoException(
                        com.nemo.nemo.common.exception.ErrorCode.MEMBER_NOT_FOUND));

        albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, target.getId())
                .ifPresent(am -> { throw new com.nemo.nemo.common.exception.NemoException(
                        com.nemo.nemo.common.exception.ErrorCode.MEMBER_ALREADY_IN_ALBUM); });

        com.nemo.nemo.domain.album.entity.Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new com.nemo.nemo.common.exception.NemoException(
                        com.nemo.nemo.common.exception.ErrorCode.ALBUM_NOT_FOUND));

        AlbumMember newMember = AlbumMember.create(album, target, role, MemberStatus.PENDING);
        albumMemberRepository.save(newMember);
        roleCacheService.invalidate(albumId.toString(), target.getId().toString());

        notificationService.send(target.getId().toString(), NotificationType.ALBUM_INVITATION,
                Map.of("albumId", albumId.toString(), "albumName", album.getName()));
    }

    // ADMIN 권한 위임: 대상은 ADMIN, 기존 ADMIN은 EDITOR로 강등
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
