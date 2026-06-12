package com.nemo.nemo.domain.excalidraw.service;

import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import com.nemo.nemo.domain.excalidraw.repository.ExcalidrawPageRepository;
import com.nemo.nemo.domain.sync.service.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("TombstoneGCTask")
@ExtendWith(MockitoExtension.class)
class TombstoneGCTaskTest {

    @Mock ExcalidrawPageRepository pageRepository;
    @Mock PageDocumentStore pageDocumentStore;
    @Mock RoomManager roomManager;
    @InjectMocks TombstoneGCTask gcTask;

    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // gcTask는 @InjectMocks로 생성되는데 ObjectMapper 실제 인스턴스 주입 필요
        gcTask = new TombstoneGCTask(pageRepository, pageDocumentStore, roomManager, realMapper);
    }

    private ExcalidrawPage page(UUID albumUuid, String elements, LocalDateTime deletedAt) {
        Album album = Album.create("test", null, UUID.randomUUID());
        try {
            var f = Album.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(album, albumUuid);
        } catch (Exception ignored) {}

        ExcalidrawPage page = ExcalidrawPage.create(album, "p", 0);
        try {
            var f = ExcalidrawPage.class.getDeclaredField("pageId");
            f.setAccessible(true);
            f.set(page, UUID.randomUUID());
        } catch (Exception ignored) {}

        if (elements != null) page.updateElements(elements, 1L);
        if (deletedAt != null) {
            try {
                var f = ExcalidrawPage.class.getDeclaredField("deletedAt");
                f.setAccessible(true);
                f.set(page, deletedAt);
            } catch (Exception ignored) {}
        }
        return page;
    }

    @Test
    @DisplayName("deletedAt!=null인 page는 skip")
    void deletedPage_skipped() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId, "[{\"isDeleted\":true}]", LocalDateTime.now());
        given(pageRepository.findAll()).willReturn(List.of(p));

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
        verify(pageDocumentStore, never()).evict(anyString());
    }

    @Test
    @DisplayName("앨범에 활성 세션 있으면 skip")
    void activeSession_skipped() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId, "[{\"isDeleted\":true}]", null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(false);

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
    }

    @Test
    @DisplayName("elements가 null → skip")
    void nullElements_skipped() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId, null, null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(true);

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
    }

    @Test
    @DisplayName("elements가 '[]' → skip")
    void emptyArrayElements_skipped() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId, "[]", null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(true);

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
    }

    @Test
    @DisplayName("tombstone 없는 정상 elements → save 하지 않음")
    void noTombstones_notSaved() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId,
                "[{\"id\":\"a\",\"isDeleted\":false},{\"id\":\"b\",\"isDeleted\":false}]", null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(true);

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
    }

    @Test
    @DisplayName("tombstone 포함 → cleaned 저장 + evict 호출")
    void tombstones_cleanedAndEvicted() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId,
                "[{\"id\":\"a\",\"isDeleted\":false},{\"id\":\"b\",\"isDeleted\":true}]", null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(true);

        gcTask.runGC();

        verify(pageRepository, times(1)).save(p);
        verify(pageDocumentStore, times(1)).evict(p.getPageId().toString());
    }

    @Test
    @DisplayName("잘못된 JSON → catch 블록, save 안 됨")
    void invalidJson_caught() {
        UUID albumId = UUID.randomUUID();
        ExcalidrawPage p = page(albumId, "not-json-{}", null);
        given(pageRepository.findAll()).willReturn(List.of(p));
        given(roomManager.isEmpty(albumId.toString())).willReturn(true);

        gcTask.runGC();

        verify(pageRepository, never()).save(any());
    }
}
