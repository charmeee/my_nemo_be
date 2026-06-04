package com.nemo.nemo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh Token을 찾을 수 없습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // Album
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "앨범을 찾을 수 없습니다."),
    ALBUM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "앨범에 접근 권한이 없습니다."),
    ALBUM_EDIT_DENIED(HttpStatus.FORBIDDEN, "앨범 편집 권한이 없습니다."),
    ALBUM_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    ALBUM_LOCKED(HttpStatus.FORBIDDEN, "잠긴 앨범입니다."),
    ALBUM_PAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "페이지 수 제한(30개)을 초과했습니다."),
    ALBUM_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "앨범 이름은 30자 이하여야 합니다."),

    // Album Member
    MEMBER_ALREADY_IN_ALBUM(HttpStatus.CONFLICT, "이미 앨범에 참여한 사용자입니다."),
    MEMBER_NOT_IN_ALBUM(HttpStatus.NOT_FOUND, "앨범 멤버를 찾을 수 없습니다."),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 추방할 수 없습니다."),
    ADMIN_MUST_TRANSFER(HttpStatus.BAD_REQUEST, "관리자는 권한 이관 후 탈퇴할 수 있습니다."),

    // Invite
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다."),
    INVITE_INACTIVE(HttpStatus.GONE, "비활성화된 초대 링크입니다."),

    // Image
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지 크기는 10MB 이하여야 합니다."),
    IMAGE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // Trash
    TRASH_NOT_FOUND(HttpStatus.NOT_FOUND, "휴지통 항목을 찾을 수 없습니다."),

    // Common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
