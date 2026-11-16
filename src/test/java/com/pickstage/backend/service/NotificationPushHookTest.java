package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pickstage.backend.dto.CustomNotificationRequest;
import com.pickstage.backend.entity.Bookmark;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.scheduler.NotificationScheduler;

// 푸시는 "이미 발송된 알림"에만 붙는다: 공연 당일, 댓글, 예약 알림이 발송 시각이 되어 발송 처리될 때
class NotificationPushHookTest {

    private NotificationRepository notificationRepository;
    private FestivalRepository festivalRepository;
    private BookmarkRepository bookmarkRepository;
    private PushService pushService;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        festivalRepository = mock(FestivalRepository.class);
        bookmarkRepository = mock(BookmarkRepository.class);
        pushService = mock(PushService.class);
        service = new NotificationService(notificationRepository, festivalRepository, bookmarkRepository, pushService);
        // save는 받은 알림을 그대로 돌려주고, id를 붙여준다
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private List<Notification> pushed() {
        ArgumentCaptor<Collection<Notification>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pushService).pushAfterCommit(captor.capture());
        return List.copyOf(captor.getValue());
    }

    @Test
    void 공연_당일_알림은_새로_만든_사람에게만_푸시한다() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        Festival festival = Festival.builder().id(7L).title("락페").startDate(today).build();
        when(festivalRepository.findByStartDate(today)).thenReturn(List.of(festival));
        // 사용자 2는 이미 받았다
        when(notificationRepository.findByFestivalIdAndType(7L, Notification.Type.concert_day)).thenReturn(List.of(
                Notification.builder().userId(2L).festivalId(7L).type(Notification.Type.concert_day).build()));
        when(bookmarkRepository.findByFestivalId(7L)).thenReturn(List.of(
                Bookmark.builder().userId(1L).festivalId(7L).build(),
                Bookmark.builder().userId(2L).festivalId(7L).build(),
                Bookmark.builder().userId(3L).festivalId(7L).build()));

        int created = service.createConcertDayNotifications(today);

        assertEquals(2, created);
        List<Notification> pushed = pushed();
        assertEquals(List.of(1L, 3L), pushed.stream().map(Notification::getUserId).toList());
        assertTrue(pushed.stream().allMatch(Notification::isSent));
    }

    @Test
    void 공연_당일_알림을_새로_만들지_않았으면_푸시할_것이_없다() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        when(festivalRepository.findByStartDate(today)).thenReturn(List.of());

        service.createConcertDayNotifications(today);

        // 빈 목록으로 부르는 것은 PushService가 알아서 무시한다
        ArgumentCaptor<Collection<Notification>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pushService).pushAfterCommit(captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void 댓글_알림은_글쓴이에게_푸시한다() {
        Post post = Post.builder().id(42L).userId(1L).title("후기").build();

        service.notifyNewComment(post, 2L, "닉네임");

        List<Notification> pushed = pushed();
        assertEquals(1, pushed.size());
        assertEquals(1L, pushed.get(0).getUserId());
        assertEquals(42L, pushed.get(0).getPostId());
        assertEquals(Notification.Type.comment, pushed.get(0).getType());
    }

    @Test
    void 내가_내_글에_단_댓글은_푸시하지_않는다() {
        service.notifyNewComment(Post.builder().id(42L).userId(1L).title("후기").build(), 1L, "나");

        verify(pushService, never()).pushAfterCommit(anyCollection());
    }

    @Test
    void 사용자_지정_알림은_예약만_하고_만드는_순간에는_푸시하지_않는다() {
        Festival festival = Festival.builder().id(7L).title("락페").startDate(LocalDate.now().plusDays(30)).build();
        when(festivalRepository.findById(7L)).thenReturn(Optional.of(festival));
        when(notificationRepository.findByUserIdAndFestivalIdAndTypeAndIsSentFalseOrderByScheduledAtAsc(
                1L, 7L, Notification.Type.custom)).thenReturn(List.of());
        CustomNotificationRequest request = new CustomNotificationRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "festivalId", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "scheduledAt", LocalDateTime.now().plusDays(5));

        service.createCustomNotification(1L, request);

        verify(pushService, never()).pushAfterCommit(anyCollection());
    }

    @Test
    void 스케줄러는_발송_시각이_된_예약_알림을_발송_처리하고_푸시한다() {
        Notification due1 = Notification.builder().id(1L).userId(1L).type(Notification.Type.ticket_open).build();
        Notification due2 = Notification.builder().id(2L).userId(2L).type(Notification.Type.custom).build();
        when(notificationRepository.findByIsSentFalseAndScheduledAtLessThanEqual(any(LocalDateTime.class)))
                .thenReturn(List.of(due1, due2));
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, service, pushService);

        scheduler.sendDueNotifications();

        assertTrue(due1.isSent());
        assertTrue(due2.isSent());
        assertEquals(List.of(due1, due2), pushed());
    }
}
