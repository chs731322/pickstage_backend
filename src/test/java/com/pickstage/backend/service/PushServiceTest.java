package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.pickstage.backend.dto.PushTokenResponse;
import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.entity.PushToken;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PushTokenRepository;

class PushServiceTest {

    private static final String TOKEN_A = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";
    private static final String TOKEN_B = "ExponentPushToken[bbbbbbbbbbbbbbbbbbbbbb]";

    private PushTokenRepository tokenRepository;
    private NotificationRepository notificationRepository;
    private ExpoPushClient client;
    private PushService service;
    private MutableClock clock;

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-16T03:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void setUp() {
        tokenRepository = mock(PushTokenRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        client = mock(ExpoPushClient.class);
        clock = new MutableClock();
        // 같은 스레드에서 바로 실행
        service = new PushService(tokenRepository, notificationRepository, client, Runnable::run, clock);
        when(tokenRepository.save(any(PushToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PushToken token(Long userId, String value) {
        return PushToken.builder().userId(userId).token(value).platform("ios").enabled(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private Notification notification(Long id, Long userId, Notification.Type type, Long festivalId, Long postId) {
        return Notification.builder().id(id).userId(userId).type(type).festivalId(festivalId).postId(postId)
                .title("제목" + id).message("내용" + id).isSent(true).build();
    }

    private List<ExpoPushClient.Ticket> allOk(int n) {
        List<ExpoPushClient.Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tickets.add(new ExpoPushClient.Ticket(true, null, null));
        }
        return tickets;
    }

    @SuppressWarnings("unchecked")
    private void sendsAllOk() {
        when(client.send(anyList())).thenAnswer(inv -> allOk(((List<Object>) inv.getArgument(0)).size()));
    }

    // ================= 기기 등록 =================

    @Test
    void 새_기기를_등록하면_푸시가_켜진_상태로_저장된다() {
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.empty());
        when(tokenRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());

        PushTokenResponse response = service.register(1L, "  " + TOKEN_A + "  ", " iOS ");

        assertTrue(response.isEnabled());
        ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(TOKEN_A, captor.getValue().getToken());
        assertEquals("ios", captor.getValue().getPlatform());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void 같은_계정이_다시_등록해도_꺼둔_설정은_유지된다() {
        PushToken existing = token(1L, TOKEN_A);
        existing.setEnabled(false);
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(existing));
        when(tokenRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(existing));

        PushTokenResponse response = service.register(1L, TOKEN_A, "android");

        assertFalse(response.isEnabled());
        assertEquals("android", existing.getPlatform());
    }

    @Test
    void 다른_계정이_쓰던_기기는_새_계정으로_옮기고_다시_켠다() {
        PushToken existing = token(1L, TOKEN_A);
        existing.setEnabled(false);
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(existing));
        when(tokenRepository.findByUserIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of(existing));

        PushTokenResponse response = service.register(2L, TOKEN_A, "ios");

        assertTrue(response.isEnabled());
        assertEquals(2L, existing.getUserId());
    }

    @Test
    void 형식이_틀린_토큰과_기기_종류는_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, "abc", "ios"));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, "ExponentPushToken[]", "ios"));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, null, "ios"));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, TOKEN_A, "windows"));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, TOKEN_A, null));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void 한_계정에_기기가_10대를_넘으면_가장_오래_안_쓴_기기부터_지운다() {
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.empty());
        List<PushToken> devices = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            devices.add(token(1L, "ExponentPushToken[device" + String.format("%02d", i) + "xxxxxxxx]"));
        }
        when(tokenRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(devices);

        service.register(1L, TOKEN_A, "ios");

        ArgumentCaptor<List<PushToken>> removed = ArgumentCaptor.forClass(List.class);
        verify(tokenRepository).deleteAll(removed.capture());
        assertEquals(2, removed.getValue().size());
        assertEquals(devices.get(10), removed.getValue().get(0));
    }

    // ================= 해제·켜기끄기 =================

    @Test
    void 해제는_내_기기만_지운다() {
        PushToken mine = token(1L, TOKEN_A);
        PushToken others = token(2L, TOKEN_B);
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(mine));
        when(tokenRepository.findByToken(TOKEN_B)).thenReturn(Optional.of(others));

        service.unregister(1L, TOKEN_A);
        service.unregister(1L, TOKEN_B);
        service.unregister(1L, null);

        verify(tokenRepository).delete(mine);
        verify(tokenRepository, never()).delete(others);
    }

    @Test
    void 푸시를_껐다_켤_수_있고_남의_기기나_없는_기기는_404다() {
        PushToken mine = token(1L, TOKEN_A);
        when(tokenRepository.findByToken(TOKEN_A)).thenReturn(Optional.of(mine));
        when(tokenRepository.findByToken(TOKEN_B)).thenReturn(Optional.of(token(2L, TOKEN_B)));

        assertFalse(service.setEnabled(1L, TOKEN_A, false).isEnabled());
        assertFalse(mine.isEnabled());
        assertTrue(service.setEnabled(1L, TOKEN_A, true).isEnabled());

        assertThrows(IllegalStateException.class, () -> service.setEnabled(1L, TOKEN_B, true));
        assertThrows(IllegalStateException.class, () -> service.setEnabled(1L, "ExponentPushToken[nothing000000]", true));
    }

    // ================= 푸시 보내기 =================

    @Test
    void 알림을_받는_사람의_모든_기기로_제목_내용_이동_정보와_배지를_담아_보낸다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection()))
                .thenReturn(List.of(token(1L, TOKEN_A), token(1L, TOKEN_B)));
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.<Object[]>of(new Object[] { 1L, 3L }));
        sendsAllOk();

        service.push(List.of(notification(10L, 1L, Notification.Type.ticket_open, 77L, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).send(sent.capture());
        assertEquals(2, sent.getValue().size());
        Map<String, Object> first = sent.getValue().get(0);
        assertEquals(TOKEN_A, first.get("to"));
        assertEquals("제목10", first.get("title"));
        assertEquals("내용10", first.get("body"));
        assertEquals("default", first.get("sound"));
        assertEquals(3L, first.get("badge"));
        assertEquals(Map.of("type", "ticket_open", "notificationId", 10L, "festivalId", 77L), first.get("data"));
        assertEquals(TOKEN_B, sent.getValue().get(1).get("to"));
    }

    @Test
    void 댓글_알림은_게시글로_이동할_정보를_담는다() {
        Map<String, Object> data = PushService.data(notification(5L, 1L, Notification.Type.comment, null, 42L));

        assertEquals(Map.of("type", "comment", "notificationId", 5L, "postId", 42L), data);
    }

    @Test
    void 푸시를_켠_기기가_없는_사람은_건너뛰고_아무도_없으면_보내지_않는다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(2L, TOKEN_B)));
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.of());
        sendsAllOk();

        service.push(List.of(
                notification(1L, 1L, Notification.Type.custom, 1L, null),     // 기기 없음
                notification(2L, 2L, Notification.Type.custom, 1L, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).send(sent.capture());
        assertEquals(1, sent.getValue().size());
        assertEquals(TOKEN_B, sent.getValue().get(0).get("to"));
        assertFalse(sent.getValue().get(0).containsKey("badge"));

        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of());
        service.push(List.of(notification(3L, 3L, Notification.Type.custom, 1L, null)));
        verify(client, times(1)).send(anyList());
    }

    @Test
    void 푸시는_100개씩_나눠서_보낸다() {
        List<PushToken> tokens = new ArrayList<>();
        List<Notification> notifications = new ArrayList<>();
        for (long user = 1; user <= 250; user++) {
            tokens.add(token(user, "ExponentPushToken[user" + String.format("%04d", user) + "xxxxxx]"));
            notifications.add(notification(user, user, Notification.Type.concert_day, 1L, null));
        }
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(tokens);
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.of());
        sendsAllOk();

        service.push(notifications);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> sent = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).send(sent.capture());
        assertEquals(List.of(100, 100, 50), sent.getAllValues().stream().map(List::size).toList());
    }

    @Test
    void 더는_유효하지_않은_토큰만_골라_지운다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection()))
                .thenReturn(List.of(token(1L, TOKEN_A), token(2L, TOKEN_B)));
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.of());
        when(client.send(anyList())).thenReturn(List.of(
                new ExpoPushClient.Ticket(false, "DeviceNotRegistered", "x"),
                new ExpoPushClient.Ticket(false, "MessageRateExceeded", "y")));

        service.push(List.of(
                notification(1L, 1L, Notification.Type.custom, 1L, null),
                notification(2L, 2L, Notification.Type.custom, 1L, null)));

        verify(tokenRepository).deleteAllByTokenIn(Set.of(TOKEN_A));
    }

    // ================= 비동기·커밋 뒤 보내기 =================

    @Test
    void 푸시_서버가_실패해도_예외가_밖으로_나가지_않는다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(1L, TOKEN_A)));
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.of());
        when(client.send(anyList())).thenThrow(new ExternalServiceUnavailableException("서버 오류"));

        service.pushAfterCommit(List.of(notification(1L, 1L, Notification.Type.custom, 1L, null)));

        verify(client).send(anyList());
    }

    @Test
    void 트랜잭션_안에서는_커밋된_뒤에야_보낸다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(1L, TOKEN_A)));
        when(notificationRepository.countUnreadByUserIds(anyCollection())).thenReturn(List.of());
        sendsAllOk();

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.pushAfterCommit(List.of(notification(1L, 1L, Notification.Type.custom, 1L, null)));
            verify(client, never()).send(anyList());   // 아직 커밋 전

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(client).send(anyList());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 롤백되면_보내지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.pushAfterCommit(List.of(notification(1L, 1L, Notification.Type.custom, 1L, null)));
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(client, never()).send(anyList());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 보낼_알림이_없으면_아무것도_하지_않는다() {
        service.pushAfterCommit(List.of());
        service.pushAfterCommit(null);

        verify(tokenRepository, never()).findByUserIdInAndEnabledTrue(anyCollection());
    }

    // ================= 테스트 알림 =================

    @Test
    void 테스트_알림은_내_기기로_보내고_1분에_한_번만_보낼_수_있다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(1L, TOKEN_A)));
        sendsAllOk();

        assertEquals(1, service.sendTest(1L));
        assertThrows(DuplicateRequestException.class, () -> service.sendTest(1L));

        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertEquals(1, service.sendTest(1L));
        // 다른 계정은 따로 센다
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(2L, TOKEN_B)));
        assertEquals(1, service.sendTest(2L));
    }

    @Test
    void 푸시를_받을_기기가_없으면_테스트_알림을_보낼_수_없다() {
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.sendTest(1L));
        verify(client, never()).send(anyList());
        // 기기가 없어서 실패한 것은 대기 시간으로 세지 않는다
        when(tokenRepository.findByUserIdInAndEnabledTrue(anyCollection())).thenReturn(List.of(token(1L, TOKEN_A)));
        sendsAllOk();
        assertNotNull(service.sendTest(1L));
    }
}
