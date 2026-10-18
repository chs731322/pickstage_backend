package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.NotificationContent;
import com.pickstage.backend.dto.NotificationResponse;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.NotificationRepository;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final FestivalRepository festivalRepository;

    public NotificationService(NotificationRepository notificationRepository, FestivalRepository festivalRepository) {
        this.notificationRepository = notificationRepository;
        this.festivalRepository = festivalRepository;
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

        Notification notification = Notification.builder()
                .userId(userId)
                .festivalId(festival.getId())
                .type(Notification.Type.ticket_open)
                .title("티켓 오픈 알림")
                .message("저장하신 " + festival.getTitle() + " 티켓이 곧 오픈됩니다.")
                .isSent(false)
                .isRead(false)
                .scheduledAt(festival.getTicketOpenDate())
                .build();

        notificationRepository.save(notification);
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
