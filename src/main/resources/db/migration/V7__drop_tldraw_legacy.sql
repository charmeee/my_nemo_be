-- V7: TLDraw 레거시 테이블 제거
-- V5 이후 신규 앨범부터 사용하지 않으며, Java 엔티티/리포지토리도 제거됨.

DROP INDEX IF EXISTS idx_album_pages_album_id;
DROP INDEX IF EXISTS idx_album_pages_deleted_at;
DROP TABLE IF EXISTS album_pages;
DROP TABLE IF EXISTS tldraw_documents;
