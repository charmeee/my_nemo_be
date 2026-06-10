package com.nemo.nemo.unit.notification;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1-G 알림 트리거 단위 테스트 — E2E에서 누락된 항목
 * TC-NOTIF-03~10
 */
@DisplayName("1-G Notification Unit Tests")
class NotificationUnitTest extends ApiE2ETestBase {

    private boolean hasNotifType(String token, String type) throws Exception {
        var notifs = json(get("/notifications", token)).path("data");
        for (var n : notifs) {
            if (type.equals(n.path("type").asText())) return true;
        }
        return false;
    }

    @Test
    @DisplayName("TC-NOTIF-03: 승인 필요 join → ADMIN에게 JOIN_REQUEST 알림")
    void notif03_joinRequest_adminGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 요청알림");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", true);

        // Bob 참여 요청 → PENDING
        joinViaInvite(bobToken, code);

        assertThat(hasNotifType(aliceToken, "JOIN_REQUEST")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-04: join 승인 → Bob에게 JOIN_APPROVED 알림")
    void notif04_joinApproved_bobGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 승인알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", true));

        // Alice가 승인
        post("/albums/" + albumId + "/members/" + bobId + "/approve", aliceToken, null);

        assertThat(hasNotifType(bobToken, "JOIN_APPROVED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-05: join 거절 → Bob에게 JOIN_REJECTED 알림")
    void notif05_joinRejected_bobGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 거절알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", true));

        // Alice가 거절
        post("/albums/" + albumId + "/members/" + bobId + "/reject", aliceToken, null);

        assertThat(hasNotifType(bobToken, "JOIN_REJECTED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-06: 페이지 추가 → 멤버에게 NEW_PAGE_ADDED 알림")
    void notif06_newPage_memberGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 페이지알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Bob이 페이지 추가
        post("/albums/" + albumId + "/pages", bobToken, "{\"name\":\"새페이지\"}");

        assertThat(hasNotifType(aliceToken, "NEW_PAGE_ADDED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-07: 앨범 이름/커버 변경 → 멤버에게 ALBUM_UPDATED 알림")
    void notif07_albumUpdated_memberGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 앨범업데이트알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Alice가 앨범 이름 변경
        patch("/albums/" + albumId, aliceToken, "{\"name\":\"이름변경됨\"}");

        assertThat(hasNotifType(bobToken, "ALBUM_UPDATED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-08: 멤버 앨범 나감 → ADMIN에게 MEMBER_LEFT 알림")
    void notif08_memberLeft_adminGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 탈퇴알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Bob 탈퇴
        delete("/albums/" + albumId + "/members/me", bobToken);

        assertThat(hasNotifType(aliceToken, "MEMBER_LEFT")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-09: 역할 변경 → 해당 멤버에게 ROLE_CHANGED 알림")
    void notif09_roleChanged_memberGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 역할변경알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Alice가 Bob 역할 변경
        patch("/albums/" + albumId + "/members/" + bobId, aliceToken, "{\"role\":\"VIEWER\"}");

        assertThat(hasNotifType(bobToken, "ROLE_CHANGED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-10: 앨범 잠금 → 멤버에게 ALBUM_LOCKED 알림")
    void notif10_albumLocked_memberGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠금알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Alice가 잠금
        patch("/albums/" + albumId, aliceToken, "{\"isLocked\":true}");

        assertThat(hasNotifType(bobToken, "ALBUM_LOCKED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-10b: 앨범 잠금 해제 → 멤버에게 ALBUM_UNLOCKED 알림")
    void notif10b_albumUnlocked_memberGetsNotification() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 잠금해제알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        patch("/albums/" + albumId, aliceToken, "{\"isLocked\":true}");
        patch("/albums/" + albumId, aliceToken, "{\"isLocked\":false}");

        assertThat(hasNotifType(bobToken, "ALBUM_UNLOCKED")).isTrue();
    }

    @Test
    @DisplayName("TC-NOTIF-11: 동일 알림 5분 내 2회 → 중복 발송 없음 (suppress)")
    void notif11_notifDedup_noDuplicate() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 알림중복방지");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // 짧은 시간 내 동일 이벤트 2회 발생 (앨범 이름 두 번 변경)
        patch("/albums/" + albumId, aliceToken, "{\"name\":\"변경1\"}");
        patch("/albums/" + albumId, aliceToken, "{\"name\":\"변경2\"}");

        // ALBUM_UPDATED 알림이 1개만 존재해야 함 (suppress 동작)
        var notifs = json(get("/notifications", bobToken)).path("data");
        long count = 0;
        for (var n : notifs) {
            if ("ALBUM_UPDATED".equals(n.path("type").asText())) count++;
        }
        assertThat(count).isEqualTo(1);
    }
}
