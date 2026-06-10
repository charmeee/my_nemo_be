package com.nemo.nemo.domain.album.service;

import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.dto.AlbumCreateRequest;
import com.nemo.nemo.domain.album.dto.AlbumResponse;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.notification.service.NotificationService;
import com.nemo.nemo.domain.trash.service.TrashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("AlbumService")
@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock AlbumRepository albumRepository;
    @Mock AlbumMemberRepository albumMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock TrashService trashService;
    @Mock NotificationService notificationService;
    @Mock InviteLinkRepository inviteLinkRepository;
    @Mock ExcalidrawPageRepository excalidrawPageRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AlbumService albumService;

    private UUID userId;
    private UUID albumId;
    private Member member;
    private Album album;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        albumId = UUID.randomUUID();
        member = mock(Member.class);
        album = Album.create("테스트 앨범", null, userId);
        ReflectionTestUtils.setField(album, "id", albumId);
    }

    @Nested
    @DisplayName("createAlbum")
    class CreateAlbum {

        @Test
        @DisplayName("앨범 생성 성공 - ADMIN 멤버로 등록")
        void 생성_성공() {
            given(memberRepository.findById(userId)).willReturn(Optional.of(member));
            // albumRepository.save() 호출 시 생성된 앨범에 ID를 주입
            doAnswer(invocation -> {
                Album saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", albumId);
                return saved;
            }).when(albumRepository).save(any(Album.class));
            given(albumMemberRepository.countByAlbumIdAndStatus(any(), eq(MemberStatus.ACTIVE))).willReturn(1L);
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(any(), eq(userId)))
                    .willReturn(Optional.of(AlbumMember.create(album, member, AlbumRole.ADMIN, MemberStatus.ACTIVE)));

            AlbumResponse resp = albumService.createAlbum(userId, new AlbumCreateRequest("테스트 앨범", null));

            assertThat(resp.name()).isEqualTo("테스트 앨범");
            assertThat(resp.myRole()).isEqualTo("ADMIN");
            verify(albumMemberRepository).save(any(AlbumMember.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - MEMBER_NOT_FOUND")
        void 없는_사용자_예외() {
            given(memberRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> albumService.createAlbum(userId, new AlbumCreateRequest("앨범", null)))
                    .isInstanceOf(NemoException.class);
        }
    }

    @Nested
    @DisplayName("deleteAlbum")
    class DeleteAlbum {

        @Test
        @DisplayName("ADMIN이 삭제 - 성공")
        void admin_삭제_성공() {
            AlbumMember adminMember = AlbumMember.create(album, member, AlbumRole.ADMIN, MemberStatus.ACTIVE);
            given(albumRepository.findById(albumId)).willReturn(Optional.of(album));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId))
                    .willReturn(Optional.of(adminMember));

            albumService.deleteAlbum(albumId, userId);

            assertThat(album.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("EDITOR가 삭제 시도 - ALBUM_ADMIN_REQUIRED")
        void editor_삭제_실패() {
            AlbumMember editorMember = AlbumMember.create(album, member, AlbumRole.EDITOR, MemberStatus.ACTIVE);
            given(albumRepository.findById(albumId)).willReturn(Optional.of(album));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId))
                    .willReturn(Optional.of(editorMember));

            assertThatThrownBy(() -> albumService.deleteAlbum(albumId, userId))
                    .isInstanceOf(NemoException.class);
        }

        @Test
        @DisplayName("존재하지 않는 앨범 - ALBUM_NOT_FOUND")
        void 없는_앨범_예외() {
            given(albumRepository.findById(albumId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> albumService.deleteAlbum(albumId, userId))
                    .isInstanceOf(NemoException.class);
        }

        @Test
        @DisplayName("멤버가 아닌 사용자 - ALBUM_ACCESS_DENIED")
        void 비멤버_접근_예외() {
            given(albumRepository.findById(albumId)).willReturn(Optional.of(album));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> albumService.deleteAlbum(albumId, userId))
                    .isInstanceOf(NemoException.class);
        }
    }

    @Nested
    @DisplayName("getAlbum")
    class GetAlbum {

        @Test
        @DisplayName("멤버가 조회 - 성공")
        void 멤버_조회_성공() {
            AlbumMember editorMember = AlbumMember.create(album, member, AlbumRole.EDITOR, MemberStatus.ACTIVE);
            given(albumRepository.findById(albumId)).willReturn(Optional.of(album));
            given(albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId))
                    .willReturn(Optional.of(editorMember));
            given(albumMemberRepository.countByAlbumIdAndStatus(any(), eq(MemberStatus.ACTIVE))).willReturn(2L);

            AlbumResponse resp = albumService.getAlbum(albumId, userId);

            assertThat(resp.myRole()).isEqualTo("EDITOR");
            assertThat(resp.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("소프트 삭제된 앨범 - ALBUM_NOT_FOUND")
        void 삭제된_앨범_예외() {
            album.softDelete();
            given(albumRepository.findById(albumId)).willReturn(Optional.of(album));

            assertThatThrownBy(() -> albumService.getAlbum(albumId, userId))
                    .isInstanceOf(NemoException.class);
        }
    }
}
