-- V4: 이메일/비밀번호 로그인 지원을 위한 password_hash 컬럼 추가
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
