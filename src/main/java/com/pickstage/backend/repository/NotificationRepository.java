package com.pickstage.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 목록 조회 (isRead 필터 없을 때)
    Page<Notification> findByUserIdAndIsSentTrueOrderBySentAtDesc(Long userId, Pageable pageable);

    // 알림 목록 조회 (isRead 필터 있을 때)
    Page<Notification> findByUserIdAndIsSentTrueAndIsReadOrderBySentAtDesc(
            Long userId, boolean isRead, Pageable pageable);

    // 배치 스케줄러: 발송 시각이 지난 미발송 알림 조회
    List<Notification> findByIsSentFalseAndScheduledAtLessThanEqual(LocalDateTime now);

    // 관리자 공연 삭제 시 해당 공연 관련 알림 일괄 삭제
    void deleteByFestivalId(Long festivalId);
}
