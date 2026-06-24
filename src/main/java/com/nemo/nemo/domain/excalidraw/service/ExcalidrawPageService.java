package com.nemo.nemo.domain.excalidraw.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.excalidraw.dto.PageCreateRequest;
import com.nemo.nemo.domain.excalidraw.dto.PageListResponse;
import com.nemo.nemo.domain.excalidraw.dto.PageUpdateRequest;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.image.entity.Image;
import com.nemo.nemo.domain.image.repository.ImageRepository;
import com.nemo.nemo.domain.notification.entity.NotificationType;
import com.nemo.nemo.domain.notification.service.NotificationService;
import com.nemo.nemo.domain.sync.service.RoomManager;
import com.nemo.nemo.domain.trash.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExcalidrawPageService {

    private static final int MAX_PAGES = 30;

    private final ExcalidrawPageRepository pageRepository;
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final ImageRepository imageRepository;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;
    private final PageDocumentStore pageDocumentStore;
    @Lazy
    private final NotificationService notificationService;
    @Lazy
    private final TrashService trashService;

    // 앨범 멤버 검증 후 활성 페이지 목록을 pageOrder 순으로 반환
    public List<PageListResponse> getPages(UUID albumId, UUID userId) {
        getMemberOrThrow(albumId, userId);
        return pageRepository.findByAlbumIdOrderByPageOrder(albumId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 페이지 생성: EDITOR 이상 권한 + 최대 30개 제한, 추가 후 page_event 브로드캐스트 및 알림
    @Transactional
    public PageListResponse createPage(UUID albumId, UUID userId, PageCreateRequest req) {
        requireEditor(albumId, userId);

        long count = pageRepository.countByAlbumIdAndDeletedAtIsNull(albumId);
        if (count >= MAX_PAGES) {
            throw new NemoException(ErrorCode.ALBUM_PAGE_LIMIT_EXCEEDED);
        }

        Album album = albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        int order = (int) count;
        String name = req.name() != null && !req.name().isBlank() ? req.name() : "페이지 " + (order + 1);
        ExcalidrawPage page = ExcalidrawPage.create(album, name, order);
        pageRepository.save(page);

        broadcastPageEvent(albumId.toString(), "added", page.getPageId().toString(), name, order);

        // N-NOTIF-05: 새 페이지 추가 알림 — 모든 active 멤버에게 전송
        albumMemberRepository.findByAlbumIdAndStatus(albumId, MemberStatus.ACTIVE)
                .forEach(am -> notificationService.send(
                        am.getUser().getId().toString(),
                        NotificationType.NEW_PAGE_ADDED,
                        Map.of("albumId", albumId.toString(), "pageId", page.getPageId().toString(), "pageName", name)
                ));

        return toResponse(page);
    }

    // 페이지 이름/순서 수정: EDITOR 이상, reordered 이벤트 브로드캐스트
    @Transactional
    public PageListResponse updatePage(UUID albumId, UUID pageId, UUID userId, PageUpdateRequest req) {
        requireEditor(albumId, userId);

        ExcalidrawPage page = pageRepository.findByPageIdAndAlbumId(pageId, albumId)
                .orElseThrow(() -> new NemoException(ErrorCode.IMAGE_NOT_FOUND));

        String newName = req.name() != null ? req.name() : page.getName();
        int newOrder = req.pageOrder() != null ? req.pageOrder() : page.getPageOrder();
        page.updateMeta(newName, newOrder);

        broadcastPageEvent(albumId.toString(), "reordered", pageId.toString(), newName, newOrder);
        return toResponse(page);
    }

    // 페이지 soft delete + 휴지통 이동, deleted 이벤트 브로드캐스트
    @Transactional
    public void deletePage(UUID albumId, UUID pageId, UUID userId) {
        requireEditor(albumId, userId);

        ExcalidrawPage page = pageRepository.findByPageIdAndAlbumId(pageId, albumId)
                .orElseThrow(() -> new NemoException(ErrorCode.IMAGE_NOT_FOUND));

        page.softDelete();
        trashService.addPageToTrash(pageId, albumId, userId);
        broadcastPageEvent(albumId.toString(), "deleted", pageId.toString(), page.getName(), page.getPageOrder());
    }

    /** 특정 페이지의 현재 elements + 이미지 fileId→url 매핑 반환 (메모리→Redis→DB 순서) */
    public Map<String, Object> getPageElements(UUID albumId, UUID pageId, UUID userId) {
        getMemberOrThrow(albumId, userId);
        String json = pageDocumentStore.loadElements(pageId.toString());
        List<?> elements;
        if (json == null || json.isBlank() || json.equals("[]")) {
            elements = List.of();
        } else {
            try {
                elements = objectMapper.readValue(json, List.class);
            } catch (Exception e) {
                elements = List.of();
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("elements", elements);
        resp.put("files", loadFileMappings(albumId, collectFileIds(elements)));
        return resp;
    }

    private Set<String> collectFileIds(List<?> elements) {
        Set<String> ids = new HashSet<>();
        for (Object e : elements) {
            if (e instanceof Map<?, ?> m && "image".equals(m.get("type"))) {
                Object fid = m.get("fileId");
                if (fid instanceof String s && !s.isEmpty()) ids.add(s);
            }
        }
        return ids;
    }

    private Map<String, String> loadFileMappings(UUID albumId, Set<String> fileIds) {
        if (fileIds.isEmpty()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (Image img : imageRepository.findByAlbumIdAndExcalidrawFileIdIn(albumId, fileIds)) {
            if (img.getExcalidrawFileId() != null) {
                map.put(img.getExcalidrawFileId(), img.getUrl());
            }
        }
        return map;
    }

    /** 게스트 접근용: 멤버십 검증 없이 페이지 목록 반환 */
    public List<PageListResponse> getPublicPages(UUID albumId) {
        return pageRepository.findByAlbumIdOrderByPageOrder(albumId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void getMemberOrThrow(UUID albumId, UUID userId) {
        albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));
    }

    private void requireEditor(UUID albumId, UUID userId) {
        AlbumMember member = albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));
        if (member.getRole() == AlbumRole.VIEWER) {
            throw new NemoException(ErrorCode.ALBUM_EDIT_DENIED);
        }
    }

    private void broadcastPageEvent(String albumId, String event, String pageId, String pageName, int pageOrder) {
        Map<String, Object> msg = Map.of(
                "type", "page_event",
                "event", event,
                "pageId", pageId,
                "pageName", pageName,
                "pageOrder", pageOrder
        );
        try {
            String payload = objectMapper.writeValueAsString(msg);
            roomManager.getSessions(albumId).forEach(session -> {
                if (session.isOpen()) {
                    try {
                        synchronized (session) { session.sendMessage(new TextMessage(payload)); }
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private PageListResponse toResponse(ExcalidrawPage page) {
        return new PageListResponse(
                page.getPageId().toString(),
                page.getName(),
                page.getPageOrder(),
                null, // thumbnailUrl: 별도 업로드 API
                page.getCreatedAt() != null ? page.getCreatedAt().toString() : null
        );
    }
}
