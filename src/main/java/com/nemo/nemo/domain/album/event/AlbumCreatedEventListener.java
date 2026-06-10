package com.nemo.nemo.domain.album.event;

import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.invite.entity.InviteLink;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 앨범 생성 이벤트 리스너.
 * BEFORE_COMMIT 페이즈에서 실행되어 생성 트랜잭션과 동일한 트랜잭션 내에서 처리됩니다.
 * 초대 링크 발급(N-CORE-01)과 기본 페이지 생성을 AlbumService에서 분리합니다.
 */
@Component
@RequiredArgsConstructor
public class AlbumCreatedEventListener {

    private final AlbumRepository albumRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final ExcalidrawPageRepository excalidrawPageRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAlbumCreated(AlbumCreatedEvent event) {
        Album album = albumRepository.findById(event.albumId()).orElseThrow();

        // N-CORE-01: 초대 링크 자동 발급 (EDITOR 역할, 승인 불필요)
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        inviteLinkRepository.save(InviteLink.create(album, AlbumRole.EDITOR, false, code));

        // 기본 페이지 생성
        excalidrawPageRepository.save(ExcalidrawPage.create(album, "페이지 1", 1));
    }
}
