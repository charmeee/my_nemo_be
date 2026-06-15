-- V6: Excalidraw fileId(SHA-1) ↔ images.url 매핑을 위해 images에 excalidraw_file_id 컬럼 추가
-- Excalidraw 클라이언트가 이미지 binary 내용을 SHA-1로 해시해 만든 값이며,
-- 같은 앨범 내에서 fileId로 URL을 역조회하는 데 사용된다.

ALTER TABLE images ADD COLUMN excalidraw_file_id VARCHAR(64);

CREATE INDEX idx_images_album_excalidraw_file_id
    ON images (album_id, excalidraw_file_id);
