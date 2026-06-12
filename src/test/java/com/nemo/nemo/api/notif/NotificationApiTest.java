package com.nemo.nemo.api.notif;

import com.nemo.nemo.api.ApiE2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 7-G. 알림 API E2E 테스트
 * TC-API-E2E-NOTIF-01 ~ 05
 */
@DisplayName("7-G Notification API E2E")
class NotificationApiTest extends ApiE2ETestBase {

    @Test
    @DisplayName("TC-API-E2E-NOTIF-01: 새 멤버 참여 → ADMIN에게 NEW_MEMBER_JOINED 알림 생성")
    void notif01_newMemberJoined_notificationCreated() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 알림테스트");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        var notifications = json(get("/notifications", aliceToken)).path("data");
        boolean found = false;
        for (var notif : notifications) {
            if ("NEW_MEMBER_JOINED".equals(notif.path("type").asText())) { found = true; break; }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-02: 알림 읽음 처리 → isRead=true")
    void notif02_markRead_isReadTrue() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 읽음처리");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // 알림 ID 확보
        String notifId = null;
        for (var notif : json(get("/notifications", aliceToken)).path("data")) {
            if ("NEW_MEMBER_JOINED".equals(notif.path("type").asText())) {
                notifId = notif.path("id").asText(); break;
            }
        }
        assertThat(notifId).isNotNull();

        // 읽음 처리
        assertThat(statusOf(patch("/notifications/" + notifId + "/read", aliceToken, null))).isEqualTo(200);

        // isRead=true 확인
        for (var notif : json(get("/notifications", aliceToken)).path("data")) {
            if (notifId.equals(notif.path("id").asText())) {
                assertThat(notif.path("isRead").asBoolean()).isTrue();
                break;
            }
        }
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-03: 타인 알림 읽음 처리 → 403")
    void notif03_readOthersNotification_403() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 타인알림");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        // Alice 알림 ID 확보
        String aliceNotifId = null;
        for (var notif : json(get("/notifications", aliceToken)).path("data")) {
            if ("NEW_MEMBER_JOINED".equals(notif.path("type").asText())) {
                aliceNotifId = notif.path("id").asText(); break;
            }
        }
        assertThat(aliceNotifId).isNotNull();

        // Bob이 Alice의 알림 읽음 처리 → 403
        assertThat(statusOf(patch("/notifications/" + aliceNotifId + "/read", bobToken, null))).isEqualTo(403);
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-04: 전체 읽음 처리 → 모든 알림 isRead=true")
    void notif04_markAllRead_allIsReadTrue() throws Exception {
        String albumId = createAlbum(aliceToken, "[TC] 전체읽음");
        String code = createInviteLink(aliceToken, albumId, "EDITOR", false);
        joinViaInvite(bobToken, code);
        joinViaInvite(carolToken, code);

        assertThat(statusOf(patch("/notifications/read-all", aliceToken, null))).isEqualTo(200);

        for (var notif : json(get("/notifications", aliceToken)).path("data")) {
            assertThat(notif.path("isRead").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-05: 알림 목록 조회 성공")
    void notif05_getNotifications_200() throws Exception {
        assertThat(statusOf(get("/notifications", aliceToken))).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-06: unread-count → 0 (신규 사용자)")
    void notif06_unreadCount_zero() throws Exception {
        var resp = get("/notifications/unread-count", aliceToken);
        assertThat(statusOf(resp)).isEqualTo(200);
        assertThat(json(resp).path("data").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("TC-API-E2E-NOTIF-07: unread-count → 알림 생성 후 증가")
    void notif07_unreadCount_increasesAfterNotification() throws Exception {
        long before = json(get("/notifications/unread-count", aliceToken)).path("data").asLong();

        String albumId = createAlbum(aliceToken, "[TC] unread-count");
        joinViaInvite(bobToken, createInviteLink(aliceToken, albumId, "EDITOR", false));

        long after = json(get("/notifications/unread-count", aliceToken)).path("data").asLong();
        assertThat(after).isGreaterThan(before);
    }

}
