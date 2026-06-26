package com.nemo.nemo.domain.trash.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.image.entity.Image;
import com.nemo.nemo.domain.image.repository.ImageRepository;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.sync.service.RoomManager;
import org.springframework.web.socket.TextMessage;
import com.nemo.nemo.domain.trash.dto.TrashResponse;
import com.nemo.nemo.domain.trash.entity.Trash;
import com.nemo.nemo.domain.trash.entity.TrashType;
import com.nemo.nemo.domain.trash.repository.TrashRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrashService {

    private final TrashRepository trashRepository;
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final ExcalidrawPageRepository excalidrawPageRepository;
    private final ImageRepository imageRepository;
    private final MemberRepository memberRepository;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    // 앨범을 30일 만료의 휴지통 항목으로 추가
    @Transactional
    public void addAlbumToTrash(UUID albumId, UUID deletedById) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        Member member = memberRepository.findById(deletedById)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        String originalData;
        try {
            originalData = objectMapper.writeValueAsString(Map.of(
                    "name", album.getName() != null ? album.getName() : "",
                    "coverImage", album.getCoverImage() != null ? album.getCoverImage() : "",
                    "creatorId", album.getCreatorId() != null ? album.getCreatorId().toString() : ""
            ));
        } catch (JacksonException e) {
            originalData = "{}";
        }

        Trash trash = Trash.create(TrashType.ALBUM, albumId, member, originalData, 30);
        trashRepository.save(trash);
    }

    // 페이지를 30일 만료의 휴지통 항목으로 추가
    @Transactional
    public void addPageToTrash(UUID tlPageId, UUID albumId, UUID deletedById) {
        Member member = memberRepository.findById(deletedById)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        Trash trash = Trash.create(TrashType.PAGE, tlPageId, member, "{}", 30);
        trashRepository.save(trash);
    }

    // 사용자가 삭제한 휴지통 항목들을 최신순으로 반환
    public List<TrashResponse> getTrash(UUID userId) {
        return trashRepository.findByDeletedByIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 휴지통 항목 복원 (앨범/페이지), 페이지 복원 시 WS 브로드캐스트
    @Transactional
    public void restore(UUID trashId, UUID userId) {
        Trash trash = trashRepository.findById(trashId)
                .orElseThrow(() -> new NemoException(ErrorCode.TRASH_NOT_FOUND));

        if (!trash.getDeletedBy().getId().equals(userId)) {
            throw new NemoException(ErrorCode.ALBUM_ACCESS_DENIED);
        }

        if (trash.getType() == TrashType.ALBUM) {
            albumRepository.restoreAlbum(trash.getReferenceId());
        } else if (trash.getType() == TrashType.PAGE) {
            excalidrawPageRepository.findById(trash.getReferenceId()).ifPresent(page -> {
                page.restore();
                excalidrawPageRepository.save(page);
                String albumId = page.getAlbum().getId().toString();
                broadcastPageEvent(albumId, "added", page.getPageId().toString(),
                        page.getName(), page.getPageOrder());
            });
        }

        trashRepository.delete(trash);
    }

    // 휴지통 항목 영구 삭제 (앨범의 경우 이미지 파일·연관 데이터 모두 제거)
    @Transactional
    public void permanentDelete(UUID trashId, UUID userId) {
        Trash trash = trashRepository.findById(trashId)
                .orElseThrow(() -> new NemoException(ErrorCode.TRASH_NOT_FOUND));

        if (!trash.getDeletedBy().getId().equals(userId)) {
            throw new NemoException(ErrorCode.ALBUM_ACCESS_DENIED);
        }

        if (trash.getType() == TrashType.ALBUM) {
            UUID albumId = trash.getReferenceId();
            List<Image> images = imageRepository.findByAlbumIdOrderByCreatedAtDesc(albumId);
            for (Image image : images) {
                if (image.getFilePath() != null) {
                    try { Files.deleteIfExists(Paths.get(image.getFilePath())); } catch (IOException ignored) {}
                }
            }
            imageRepository.deleteAll(images);
            excalidrawPageRepository.deleteAllByAlbumId(albumId);
            albumMemberRepository.deleteAllByAlbumId(albumId);
            inviteLinkRepository.deleteAllByAlbumId(albumId);
            albumRepository.deleteById(albumId);
        } else if (trash.getType() == TrashType.PAGE) {
            excalidrawPageRepository.findById(trash.getReferenceId())
                    .ifPresent(excalidrawPageRepository::delete);
        }

        trashRepository.delete(trash);
    }

    // 매일 03시 만료된 휴지통 항목 일괄 삭제 스케줄러
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredTrash() {
        trashRepository.deleteExpired(LocalDateTime.now());
    }

    private void broadcastPageEvent(String albumId, String event, String pageId, String pageName, int pageOrder) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "page_event",
                    "event", event,
                    "pageId", pageId,
                    "pageName", pageName,
                    "pageOrder", pageOrder
            ));
            roomManager.getSessions(albumId).forEach(session -> {
                if (session.isOpen()) {
                    try {
                        synchronized (session) { session.sendMessage(new TextMessage(payload)); }
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private TrashResponse toResponse(Trash trash) {
        return new TrashResponse(
                trash.getId().toString(),
                trash.getType().name(),
                trash.getReferenceId().toString(),
                trash.getExpiresAt() != null
                        ? trash.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null,
                trash.getCreatedAt() != null
                        ? trash.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null
        );
    }
}
