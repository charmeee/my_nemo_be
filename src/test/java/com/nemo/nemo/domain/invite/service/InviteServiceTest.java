package com.nemo.nemo.domain.invite.service;

import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.invite.entity.InviteLink;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("InviteService")
@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock InviteLinkRepository inviteLinkRepository;
    @Mock AlbumRepository albumRepository;
    @Mock AlbumMemberRepository albumMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @InjectMocks InviteService inviteService;

    private UUID userId;
    private UUID albumId;
    private Member member;
    private Album album;
    private InviteLink activeLink;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        albumId = UUID.randomUUID();
        member = mock(Member.class);
        album = Album.create("테스트 앨범", null, UUID.randomUUID());
        ReflectionTestUtils.setField(album, "id", albumId);
        activeLink = InviteLink.create(album, AlbumRole.EDITOR, false, "testcode1234");
    }

    @Nested
    @DisplayName("getInviteInfo")
    class GetInviteInfo {

        @Test
        @DisplayName("비활성 링크 조회 - INVITE_INACTIVE")
        void 비활성_링크_예외() {
            activeLink.deactivate();
            given(inviteLinkRepository.findByCode("testcode1234")).willReturn(Optional.of(activeLink));

            assertThatThrownBy(() -> inviteService.getInviteInfo("testcode1234"))
                    .isInstanceOf(NemoException.class);
        }

        @Test
        @DisplayName("존재하지 않는 코드 - INVITE_NOT_FOUND")
        void 없는_코드_예외() {
            given(inviteLinkRepository.findByCode("nonexistent")).willReturn(Optional.empty());

            assertThatThrownBy(() -> inviteService.getInviteInfo("nonexistent"))
                    .isInstanceOf(NemoException.class);
        }
    }

    @Nested
    @DisplayName("joinViaInvite - 멱등성")
    class JoinViaInvite {

        @Test
        @DisplayName("이미 멤버인 경우 재가입 시도 - 조용히 무시")
        void 이미_멤버_멱등성() {
            AlbumMember existing = AlbumMember.create(album, member, AlbumRole.EDITOR, MemberStatus.ACTIVE);
            given(inviteLinkRepository.findByCode("testcode1234")).willReturn(Optional.of(activeLink));
            given(memberRepository.findById(userId)).willReturn(Optional.of(member));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(any(), eq(userId)))
                    .willReturn(Optional.of(existing));

            inviteService.joinViaInvite("testcode1234", userId);

            verify(albumMemberRepository, never()).save(any());
            verify(notificationService, never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("신규 가입 - approvalRequired=false → ACTIVE 저장")
        void 신규가입_즉시_활성화() {
            given(inviteLinkRepository.findByCode("testcode1234")).willReturn(Optional.of(activeLink));
            given(memberRepository.findById(userId)).willReturn(Optional.of(member));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(any(), eq(userId)))
                    .willReturn(Optional.empty());
            doNothing().when(notificationService).send(any(), any(), any());

            inviteService.joinViaInvite("testcode1234", userId);

            verify(albumMemberRepository).save(any(AlbumMember.class));
        }

        @Test
        @DisplayName("비활성 링크로 가입 시도 - INVITE_INACTIVE")
        void 비활성_링크_가입_예외() {
            activeLink.deactivate();
            given(inviteLinkRepository.findByCode("testcode1234")).willReturn(Optional.of(activeLink));

            assertThatThrownBy(() -> inviteService.joinViaInvite("testcode1234", userId))
                    .isInstanceOf(NemoException.class);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - MEMBER_NOT_FOUND")
        void 없는_사용자_예외() {
            given(inviteLinkRepository.findByCode("testcode1234")).willReturn(Optional.of(activeLink));
            given(memberRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inviteService.joinViaInvite("testcode1234", userId))
                    .isInstanceOf(NemoException.class);
        }
    }
}
