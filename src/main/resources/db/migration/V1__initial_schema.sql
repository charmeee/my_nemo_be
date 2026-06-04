-- V1: 초기 스키마 생성

-- 사용자
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    profile_image VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- OAuth 제공자 연결 (멀티 프로바이더 지원)
CREATE TABLE member_oauth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    UNIQUE (provider, provider_id)
);

-- 앨범
CREATE TABLE albums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(30) NOT NULL,
    cover_image VARCHAR(500),
    creator_id UUID NOT NULL REFERENCES users(id),
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 앨범 멤버 (역할 기반 권한)
CREATE TABLE album_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'editor', 'viewer')),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('pending', 'active', 'rejected')),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (album_id, user_id)
);

-- 앨범 페이지 (썸네일 메타데이터만, 구조는 TLDraw store가 관리)
CREATE TABLE album_pages (
    tl_page_id UUID PRIMARY KEY,
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    thumbnail_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- TLDraw 문서 (앨범당 1개, @tldraw/sync 상태)
CREATE TABLE tldraw_documents (
    album_id UUID PRIMARY KEY REFERENCES albums(id) ON DELETE CASCADE,
    state JSONB NOT NULL DEFAULT '{}',
    server_clock BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 이미지 (로컬 파일 스토리지 참조)
CREATE TABLE images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    uploader_id UUID NOT NULL REFERENCES users(id),
    file_path VARCHAR(500) NOT NULL,
    url VARCHAR(500) NOT NULL,
    size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 초대 링크
CREATE TABLE invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    code VARCHAR(32) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('editor', 'viewer')),
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 알림
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 휴지통
CREATE TABLE trash (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL CHECK (type IN ('album', 'page')),
    reference_id UUID NOT NULL,
    deleted_by_id UUID NOT NULL REFERENCES users(id),
    original_data JSONB NOT NULL DEFAULT '{}',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스
CREATE INDEX idx_member_oauth_member_id ON member_oauth(member_id);
CREATE INDEX idx_album_members_album_id ON album_members(album_id);
CREATE INDEX idx_album_members_user_id ON album_members(user_id);
CREATE INDEX idx_album_pages_album_id ON album_pages(album_id);
CREATE INDEX idx_images_album_id ON images(album_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_albums_deleted_at ON albums(deleted_at);
CREATE INDEX idx_album_pages_deleted_at ON album_pages(deleted_at);
