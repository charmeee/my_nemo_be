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
import com.nemo.nemo.domain.sync.service.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExcalidrawPageService {

    private static final int MAX_PAGES = 30;

    private final ExcalidrawPageRepository pageRepository;
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    public List<PageListResponse> getPages(UUID albumId, UUID userId) {
        getMemberOrThrow(albumId, userId);
        return pageRepository.findByAlbumIdOrderByPageOrder(albumId).stream()
                .map(this::toResponse)
                .toList();
    }

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
        return toResponse(page);
    }

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

    @Transactional
    public void deletePage(UUID albumId, UUID pageId, UUID userId) {
        requireEditor(albumId, userId);

        ExcalidrawPage page = pageRepository.findByPageIdAndAlbumId(pageId, albumId)
                .orElseThrow(() -> new NemoException(ErrorCode.IMAGE_NOT_FOUND));

        page.softDelete();
        broadcastPageEvent(albumId.toString(), "deleted", pageId.toString(), page.getName(), page.getPageOrder());
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
