package com.pickstage.backend.service;

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

            String festivalTitle = festivalRepository.findById(n.getFestivalId())
                    .map(festival -> festival.getTitle())
                    .orElse(null);

            return NotificationContent.builder()
                    .id(n.getId())
                    .type(n.getType().name())
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .festivalId(n.getFestivalId())
                    .festivalTitle(festivalTitle)
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
}
