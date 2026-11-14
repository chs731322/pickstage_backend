package com.pickstage.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.CustomNotificationItem;
import com.pickstage.backend.dto.CustomNotificationRequest;
import com.pickstage.backend.dto.NotificationContent;
import com.pickstage.backend.dto.NotificationResponse;
import com.pickstage.backend.entity.Bookmark;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.NotificationRepository;

@Service
public class NotificationService {
    // 사용자 지정 알림: 한 공연에 예약해 둘 수 있는 개수, 메모 길이, 예약 가능한 최대 기간
    static final int MAX_CUSTOM_PER_FESTIVAL = 3;
    static final int MAX_CUSTOM_MESSAGE_LENGTH = 50;
    static final int MAX_CUSTOM_DAYS_AHEAD = 365;

    private final NotificationRepository notificationRepository;
    private final FestivalRepository festivalRepository;
    private final BookmarkRepository bookmarkRepository;

    public NotificationService(NotificationRepository notificationRepository, FestivalRepository festivalRepository,
            BookmarkRepository bookmarkRepository) {
        this.notificationRepository = notificationRepository;
        this.festivalRepository = festivalRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // 알림 목록 조회
    @Transactional(readOnly = true)
    public NotificationResponse getNotifications(Long userId, Boolean isRead, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> notifications = (isRead == null)
                ? notificationRepository.findByUserIdAndIsSentTrueOrderBySentAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndIsSentTrueAndIsReadOrderBySentAtDesc(userId, isRead, pageable);

        Page<NotificationContent> contentPage = notifications.map(n -> {
            // n : Notification 엔티티 하나
            // NotificationContent.builder()로 하나 만들어서 리턴

            // 댓글 알림은 공연과 연결돼 있지 않아 festivalId가 없다
            String festivalTitle = n.getFestivalId() == null ? null
                    : festivalRepository.findById(n.getFestivalId())
                            .map(festival -> festival.getTitle())
                            .orElse(null);

            return NotificationContent.builder()
                    .id(n.getId())
                    .type(n.getType().name())
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .festivalId(n.getFestivalId())
                    .festivalTitle(festivalTitle)
                    .postId(n.getPostId())
                    .isRead(n.isRead())
                    .sentAt(n.getSentAt() != null ? n.getSentAt().toString() : null)
                    .build();
        });

        return NotificationResponse.builder()
                .content(contentPage.getContent())
                .totalElement((int) contentPage.getTotalElements())
                .totalPages(contentPage.getTotalPages())
                .page(contentPage.getNumber())
                .build();
    }

    // 알림 읽음 처리
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        // findById 없으면 IllegalStateException("존재하지 않는 알림입니다.")
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 알림입니다."));

        // !notification.getUserId().equals(userId)이면 AccessDeniedException("권한이 없습니다.")
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("권한이 없습니다.");
        }

        notification.markAsRead();
    }

    // 북마크 연동용
    @Transactional
    public void scheduleTicketOpenNotification(Long userId, Long festivalId) {
        // 공연 findById 없으면 예외
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));
        // festival.getTicketOpenDate()가 null이면 그냥 return (에러 아님 — 비고에 그렇게 적어놨었죠)
        if (festival.getTicketOpenDate() == null)
            return;
        // 이미 오픈 시각이 지난 공연은 알림을 예약하지 않는다 (예약하면 스케줄러가 바로 "곧 오픈됩니다"를 보내 버린다)
        if (!festival.getTicketOpenDate().isAfter(LocalDateTime.now()))
            return;
        // 이미 예약돼 있으면 또 만들지 않는다
        if (notificationRepository.existsByUserIdAndFestivalIdAndTypeAndIsSentFalse(
                userId, festivalId, Notification.Type.ticket_open))
            return;

        Notification notification = Notification.builder()
                .userId(userId)
                .festivalId(festival.getId())
                .type(Notification.Type.ticket_open)
                .title("티켓 오픈 알림")
                .message("저장하신 " + festival.getTitle() + " 티켓이 곧 오픈됩니다.")
                .isSent(false)
                .isRead(false)
                .scheduledAt(festival.getTicketOpenDate())
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    // 찜을 해제하면 아직 발송 전인 티켓 오픈 알림도 같이 지운다 (이미 받은 알림은 기록으로 남긴다)
    @Transactional
    public void cancelTicketOpenNotification(Long userId, Long festivalId) {
        notificationRepository.deleteByUserIdAndFestivalIdAndTypeAndIsSentFalse(
                userId, festivalId, Notification.Type.ticket_open);
    }

    // 공연 당일 알림: 오늘 시작하는 공연을 찜한 사용자에게 한 번씩 보낸다.
    // 스케줄러가 자주 불러도 이미 받은 사용자는 건너뛰어서 중복되지 않고, 찜 해제·공연 일정 변경도 따로 처리할 필요가 없다.
    @Transactional
    public int createConcertDayNotifications(LocalDate today) {
        int created = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Festival festival : festivalRepository.findByStartDate(today)) {
            Set<Long> alreadyNotified = new HashSet<>();
            for (Notification existing : notificationRepository.findByFestivalIdAndType(
                    festival.getId(), Notification.Type.concert_day)) {
                alreadyNotified.add(existing.getUserId());
            }

            for (Bookmark bookmark : bookmarkRepository.findByFestivalId(festival.getId())) {
                if (!alreadyNotified.add(bookmark.getUserId())) {
                    continue;
                }
                notificationRepository.save(Notification.builder()
                        .userId(bookmark.getUserId())
                        .festivalId(festival.getId())
                        .type(Notification.Type.concert_day)
                        .title("오늘은 공연 날이에요")
                        .message("저장하신 " + festival.getTitle() + " 공연이 오늘 시작돼요. 즐거운 관람 되세요!")
                        .isSent(true)
                        .isRead(false)
                        .scheduledAt(today.atTime(9, 0))
                        .sentAt(now)
                        .createdAt(now)
                        .build());
                created++;
            }
        }
        return created;
    }

    // 사용자 지정 알림 만들기: 원하는 시각에 공연 알림을 받는다
    @Transactional
    public CustomNotificationItem createCustomNotification(Long userId, CustomNotificationRequest request) {
        if (request.getFestivalId() == null || request.getScheduledAt() == null) {
            throw new IllegalArgumentException("공연과 알림 시각을 입력해주세요.");
        }
        Festival festival = festivalRepository.findById(request.getFestivalId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledAt = request.getScheduledAt().withSecond(0).withNano(0);
        if (!scheduledAt.isAfter(now)) {
            throw new IllegalArgumentException("알림 시각은 지금보다 뒤여야 해요.");
        }
        if (scheduledAt.isAfter(now.plusDays(MAX_CUSTOM_DAYS_AHEAD))) {
            throw new IllegalArgumentException("알림은 1년 안의 시각으로만 설정할 수 있어요.");
        }
        if (notificationRepository.findByUserIdAndFestivalIdAndTypeAndIsSentFalseOrderByScheduledAtAsc(
                userId, festival.getId(), Notification.Type.custom).size() >= MAX_CUSTOM_PER_FESTIVAL) {
            throw new IllegalArgumentException("한 공연에는 알림을 " + MAX_CUSTOM_PER_FESTIVAL + "개까지 설정할 수 있어요.");
        }

        String memo = request.getMessage() == null ? "" : request.getMessage().trim();
        if (memo.length() > MAX_CUSTOM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("메모는 " + MAX_CUSTOM_MESSAGE_LENGTH + "자까지 입력할 수 있어요.");
        }
        String message = memo.isEmpty() ? festival.getTitle() + " 공연 알림이에요." : memo;

        Notification saved = notificationRepository.save(Notification.builder()
                .userId(userId)
                .festivalId(festival.getId())
                .type(Notification.Type.custom)
                .title("공연 알림")
                .message(message)
                .isSent(false)
                .isRead(false)
                .scheduledAt(scheduledAt)
                .createdAt(now)
                .build());

        return toCustomItem(saved);
    }

    // 한 공연에 예약해 둔(발송 전) 사용자 지정 알림
    @Transactional(readOnly = true)
    public List<CustomNotificationItem> getPendingCustomNotifications(Long userId, Long festivalId) {
        return notificationRepository
                .findByUserIdAndFestivalIdAndTypeAndIsSentFalseOrderByScheduledAtAsc(
                        userId, festivalId, Notification.Type.custom)
                .stream().map(this::toCustomItem).toList();
    }

    // 예약해 둔 사용자 지정 알림 취소 (본인 것만, 발송 전만)
    @Transactional
    public void deleteCustomNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 알림입니다."));
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("권한이 없습니다.");
        }
        if (notification.getType() != Notification.Type.custom || notification.isSent()) {
            throw new IllegalArgumentException("예약 중인 사용자 지정 알림만 취소할 수 있어요.");
        }
        notificationRepository.delete(notification);
    }

    private CustomNotificationItem toCustomItem(Notification n) {
        return CustomNotificationItem.builder()
                .id(n.getId())
                .festivalId(n.getFestivalId())
                .scheduledAt(n.getScheduledAt())
                .message(n.getMessage())
                .build();
    }

    // 내 게시글에 다른 사람이 댓글을 달았을 때 글쓴이에게 바로 보내는 알림
    @Transactional
    public void notifyNewComment(Post post, Long commenterId, String commenterNickname) {
        // 내가 내 글에 단 댓글은 알리지 않는다
        if (post.getUserId() == null || post.getUserId().equals(commenterId)) {
            return;
        }
        String postTitle = post.getTitle() == null ? "" : post.getTitle();
        if (postTitle.length() > 30) {
            postTitle = postTitle.substring(0, 30) + "…";
        }
        LocalDateTime now = LocalDateTime.now();
        notificationRepository.save(Notification.builder()
                .userId(post.getUserId())
                .postId(post.getId())
                .type(Notification.Type.comment)
                .title("새 댓글")
                .message(commenterNickname + "님이 '" + postTitle + "' 글에 댓글을 남겼어요.")
                .isSent(true)
                .isRead(false)
                .scheduledAt(now)
                .sentAt(now)
                .createdAt(now)
                .build());
    }
}
