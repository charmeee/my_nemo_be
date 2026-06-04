-- 모든 enum 제약 조건을 대문자로 수정 (JPA EnumType.STRING 기본값은 대문자)

-- album_members
ALTER TABLE album_members DROP CONSTRAINT album_members_role_check;
ALTER TABLE album_members ADD CONSTRAINT album_members_role_check
    CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'));

ALTER TABLE album_members DROP CONSTRAINT album_members_status_check;
ALTER TABLE album_members ADD CONSTRAINT album_members_status_check
    CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED'));

-- invite_links
ALTER TABLE invite_links DROP CONSTRAINT invite_links_role_check;
ALTER TABLE invite_links ADD CONSTRAINT invite_links_role_check
    CHECK (role IN ('EDITOR', 'VIEWER'));

-- trash
ALTER TABLE trash DROP CONSTRAINT trash_type_check;
ALTER TABLE trash ADD CONSTRAINT trash_type_check
    CHECK (type IN ('ALBUM', 'PAGE'));
