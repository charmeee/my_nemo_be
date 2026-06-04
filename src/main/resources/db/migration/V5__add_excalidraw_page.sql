-- V5: Excalidraw 페이지 테이블 추가 (TLDraw → Excalidraw 마이그레이션)
-- tldraw_documents / album_pages 는 하위 호환성을 위해 유지 (신규 앨범부터 미사용)

CREATE TABLE excalidraw_pages (
    page_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id     UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL DEFAULT '페이지',
    page_order   INT NOT NULL DEFAULT 0,
    elements     TEXT NOT NULL DEFAULT '[]',
    server_clock BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE INDEX idx_excalidraw_pages_album_id ON excalidraw_pages(album_id);
CREATE INDEX idx_excalidraw_pages_deleted_at ON excalidraw_pages(deleted_at);
