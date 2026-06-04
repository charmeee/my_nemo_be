package com.nemo.nemo.domain.invite.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.invite.dto.InviteCreateRequest;
import com.nemo.nemo.domain.invite.dto.InviteInfoResponse;
import com.nemo.nemo.domain.invite.dto.InviteLinkResponse;
import com.nemo.nemo.domain.invite.entity.InviteLink;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InviteService {

    private final InviteLinkRepository inviteLinkRepository;
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final MemberRepository memberRepository;
    @Lazy
    private final NotificationService notificationService;

    @Transactional
    public InviteLinkResponse createInviteLink(UUID albumId, UUID userId, InviteCreateRequest req) {
        albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .filter(am -> am.getRole() == AlbumRole.ADMIN)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED));

        Album album = albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        InviteLink link = InviteLink.create(album, req.role(), req.approvalRequired(), code);
        inviteLinkRepository.save(link);

        return toResponse(link);
    }

    public InviteInfoResponse getInviteInfo(String code) {
        InviteLink link = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new NemoException(ErrorCode.INVITE_NOT_FOUND));

        if (!link.isActive()) {
            throw new NemoException(ErrorCode.INVITE_INACTIVE);
        }

        Album album = link.getAlbum();
        String inviterNickname = memberRepository.findById(album.getCreatorId())
                .map(m -> m.getNickname())
                .orElse(null);
        return new InviteInfoResponse(
                album.getId().toString(),
                album.getName(),
                link.getRole().name(),
                link.isApprovalRequired(),
                inviterNickname
        );
    }

    @Transactional
    public void joinViaInvite(String code, UUID userId) {
        InviteLink link = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new NemoException(ErrorCode.INVITE_NOT_FOUND));

        if (!link.isActive()) {
            throw new NemoException(ErrorCode.INVITE_INACTIVE);
        }

        Album album = link.getAlbum();
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<AlbumMember> existing = albumMemberRepository.findActiveByAlbumIdAndUserId(album.getId(), userId);
        if (existing.isPresent()) {
            return;
        }

        MemberStatus status = link.isApprovalRequired() ? MemberStatus.PENDING : MemberStatus.ACTIVE;
        AlbumMember albumMember = AlbumMember.create(album, member, link.getRole(), status);
        albumMemberRepository.save(albumMember);

        NotificationType notifType = link.isApprovalRequired()
                ? NotificationType.JOIN_REQUEST
                : NotificationType.NEW_MEMBER_JOINED;

        notificationService.send(
                album.getCreatorId().toString(),
                notifType,
                Map.of(
                        "albumId", album.getId().toString(),
                        "albumName", album.getName(),
                        "memberId", userId.toString()
                )
        );
    }

    @Transactional
    public InviteLinkResponse reissueInviteLink(UUID albumId, UUID userId) {
        albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .filter(am -> am.getRole() == AlbumRole.ADMIN)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED));

        Album album = albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        // 기존 활성 링크 모두 비활성화
        inviteLinkRepository.findByAlbumId(albumId).stream()
                .filter(InviteLink::isActive)
                .forEach(InviteLink::deactivate);

        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        InviteLink link = InviteLink.create(album, AlbumRole.EDITOR, false, code);
        inviteLinkRepository.save(link);

        return toResponse(link);
    }

    @Transactional
    public void toggleInviteLink(UUID linkId, UUID userId, boolean active) {
        InviteLink link = inviteLinkRepository.findById(linkId)
                .orElseThrow(() -> new NemoException(ErrorCode.INVITE_NOT_FOUND));

        albumMemberRepository.findActiveByAlbumIdAndUserId(link.getAlbum().getId(), userId)
                .filter(am -> am.getRole() == AlbumRole.ADMIN)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ADMIN_REQUIRED));

        if (active) {
            link.activate();
        } else {
            link.deactivate();
        }
    }

    private InviteLinkResponse toResponse(InviteLink link) {
        return new InviteLinkResponse(
                link.getId().toString(),
                link.getCode(),
                link.getRole().name(),
                link.isApprovalRequired(),
                link.isActive()
        );
    }
}
