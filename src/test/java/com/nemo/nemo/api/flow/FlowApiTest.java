package com.nemo.nemo.api.flow;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-J. 전체 플로우 통합 시나리오 E2E 테스트
 * TC-API-E2E-FLOW-01 ~ 03
 */
@DisplayName("7-J Full Flow E2E")
class FlowApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-FLOW-01: 앨범 생성 → 초대 → 참여 → 이미지 업로드 → 알림 확인")
    void flow01_createInviteJoinUploadNotify() throws Exception {
        // 1. Alice: 앨범 생성
        String albumId = createAlbum(aliceToken, "[TC-FLOW] 봄 여행");
        assertThat(albumId).isNotBlank();

        // 2. Alice: 초대 링크 생성 (EDITOR, 자동참여)
        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);
        assertThat(code).isNotBlank();

        // 3. Bob: 초대 링크로 참여
        assertThat(statusOf(joinViaInvite(bobToken, code))).isEqualTo(200);

        // 4. Bob: 이미지 업로드
        var uploadResp = upload("/albums/" + albumId + "/images", bobToken, fakejpeg("file"));
        assertThat(statusOf(uploadResp)).isEqualTo(200);
        assertThat(json(uploadResp).path("data").path("url").asText()).isNotBlank();

        // 5. Alice: 알림 목록 → NEW_MEMBER_JOINED 존재
        boolean hasNotif = false;
        for (var notif : json(get("/notifications", aliceToken)).path("data")) {
            if ("NEW_MEMBER_JOINED".equals(notif.path("type").asText())) { hasNotif = true; break; }
        }
        assertThat(hasNotif).isTrue();

        // 6. Alice: 이미지 목록 → Bob이 업로드한 이미지 포함
        var images = json(get("/albums/" + albumId + "/images", aliceToken)).path("data");
        assertThat(images.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("TC-API-E2E-FLOW-02: 승인 필요 초대 → 승인 → 역할 변경 → 추방")
    void flow02_approvalInviteApproveChangeRoleKick() throws Exception {
        // 1. Alice: 앨범 + 승인 필요 초대 링크
        String albumId = createAlbum(aliceToken, "[TC-FLOW] 승인플로우");
        String code = createInviteLink(aliceToken, albumId, "VIEWER", true);

        // 2. Carol: 참여 요청 → PENDING
        joinViaInvite(carolToken, code);

        // 3. Alice: 대기 목록 → Carol 존재
        var pendingMembers = json(get("/albums/" + albumId + "/members?status=pending", aliceToken)).path("data");
        boolean carolPending = false;
        for (var m : pendingMembers) {
            if (carolId.equals(m.path("userId").asText())) { carolPending = true; break; }
        }
        assertThat(carolPending).isTrue();

        // 4. Alice: Carol 승인
        assertThat(statusOf(post("/albums/" + albumId + "/members/" + carolId + "/approve",
                aliceToken, null))).isEqualTo(200);

        // 5. Carol: joined에 등장
        boolean carolJoinedAlbum = false;
        for (var a : json(get("/albums", carolToken)).path("data").path("joined")) {
            if (albumId.equals(a.path("id").asText())) { carolJoinedAlbum = true; break; }
        }
        assertThat(carolJoinedAlbum).isTrue();

        // 6. Alice: Carol 역할 VIEWER 유지
        assertThat(statusOf(patch("/albums/" + albumId + "/members/" + carolId,
                aliceToken, "{\"role\":\"VIEWER\"}"))).isEqualTo(200);

        // 7. Alice: Carol 추방
        assertThat(statusOf(delete("/albums/" + albumId + "/members/" + carolId, aliceToken))).isEqualTo(200);

        // 8. Carol: 앨범 접근 → 403
        assertThat(statusOf(get("/albums/" + albumId, carolToken))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-FLOW-03: 앨범 삭제 → 복원 → 영구 삭제")
    void flow03_deleteRestorePermanentDelete() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC-FLOW] 삭제복원영구");

        // 1. 삭제
        assertThat(statusOf(delete("/albums/" + albumId, aliceToken))).isEqualTo(200);

        // 2. trashId 확보
        String trashId = null;
        for (var item : json(get("/trash", aliceToken)).path("data")) {
            if (albumId.equals(item.path("referenceId").asText())) {
                trashId = item.path("id").asText(); break;
            }
        }
        assertThat(trashId).isNotNull();

        // 3. 복원
        assertThat(statusOf(post("/trash/" + trashId + "/restore", aliceToken, null))).isEqualTo(200);

        // 4. owned에 복원 확인
        boolean restored = false;
        for (var a : json(get("/albums", aliceToken)).path("data").path("owned")) {
            if (albumId.equals(a.path("id").asText())) { restored = true; break; }
        }
        assertThat(restored).isTrue();

        // 5. 다시 삭제 후 영구 삭제
        assertThat(statusOf(delete("/albums/" + albumId, aliceToken))).isEqualTo(200);

        String trashId2 = null;
        for (var item : json(get("/trash", aliceToken)).path("data")) {
            if (albumId.equals(item.path("referenceId").asText())) {
                trashId2 = item.path("id").asText(); break;
            }
        }
        assertThat(trashId2).isNotNull();

        assertThat(statusOf(delete("/trash/" + trashId2, aliceToken))).isEqualTo(200);

        // 6. 앨범 접근 → 404, 휴지통에 없음
        assertThat(statusOf(get("/albums/" + albumId, aliceToken))).isEqualTo(404);

        boolean stillInTrash = false;
        for (var item : json(get("/trash", aliceToken)).path("data")) {
            if (trashId2.equals(item.path("id").asText())) { stillInTrash = true; break; }
        }
        assertThat(stillInTrash).isFalse();
    }
}
