package com.nemo.nemo.domain.invite.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.invite.repository.InviteLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * N-CORE-13: 비회원 게스트 세션 발급.
 * POST /invite/{code}/guest-session → 24h 읽기 전용 guestToken 반환.
 * 클라이언트는 이 토큰으로 WS 연결(VIEWER 역할 고정) + GET /invite/{code}/pages 호출 가능.
 */
@RestController
@RequiredArgsConstructor
public class GuestSessionController {

    private final InviteLinkRepository inviteLinkRepository;
    private final JwtTokenService jwtTokenService;

    // 초대 코드로 24h 읽기 전용 게스트 토큰 발급
    @PostMapping("/invite/{code}/guest-session")
    public ResponseEntity<ApiResponse<Map<String, String>>> getGuestSession(
            @PathVariable String code) {
        var link = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new NemoException(ErrorCode.INVITE_NOT_FOUND));
        if (!link.isActive()) {
            throw new NemoException(ErrorCode.INVITE_INACTIVE);
        }
        String albumId = link.getAlbum().getId().toString();
        String guestToken = jwtTokenService.generateGuestToken(albumId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("guestToken", guestToken, "albumId", albumId)));
    }
}
