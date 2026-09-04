package com.pickstage.backend.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.repository.NotificationRepository;

@Component
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;

    public NotificationScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // 1분마다 발송 시각이 지난 미발송 알림을 발송 처리
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sendDueNotifications() {
        List<Notification> dueNotifications =
                notificationRepository.findByIsSentFalseAndScheduledAtLessThanEqual(LocalDateTime.now());

        for (Notification notification : dueNotifications) {
            notification.markAsSent();
        }
    }
}
