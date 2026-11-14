package com.pickstage.backend.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.service.NotificationService;

@Component
public class NotificationScheduler {

    // 공연 당일 알림을 보내기 시작하는 시각
    private static final LocalTime CONCERT_DAY_NOTIFY_FROM = LocalTime.of(9, 0);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public NotificationScheduler(NotificationRepository notificationRepository, NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    // 10분마다 확인해서, 오전 9시가 지났으면 오늘 시작하는 공연의 당일 알림을 보낸다.
    // 서버가 9시에 꺼져 있었더라도 켜진 뒤 다음 확인 때 보내고, 이미 받은 사용자는 건너뛴다.
    @Scheduled(fixedRate = 600000)
    public void sendConcertDayNotifications() {
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(CONCERT_DAY_NOTIFY_FROM)) {
            return;
        }
        notificationService.createConcertDayNotifications(now.toLocalDate());
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
