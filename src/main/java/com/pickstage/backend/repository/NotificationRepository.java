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

    // 이미 같은 예약 알림이 있는지 (찜을 해제했다 다시 눌러도 중복으로 쌓이지 않게)
    boolean existsByUserIdAndFestivalIdAndTypeAndIsSentFalse(Long userId, Long festivalId, Notification.Type type);

    // 찜 해제 시 아직 발송 전인 알림만 삭제
    void deleteByUserIdAndFestivalIdAndTypeAndIsSentFalse(Long userId, Long festivalId, Notification.Type type);

    // 공연 당일 알림을 이미 받은 사용자들
    List<Notification> findByFestivalIdAndType(Long festivalId, Notification.Type type);

    // 사용자 지정 알림: 한 공연에 예약해 둔(발송 전) 알림
    List<Notification> findByUserIdAndFestivalIdAndTypeAndIsSentFalseOrderByScheduledAtAsc(
            Long userId, Long festivalId, Notification.Type type);

    // 관리자 공연 삭제 시 해당 공연 관련 알림 일괄 삭제
    void deleteByFestivalId(Long festivalId);

    // 회원 탈퇴 시 해당 사용자의 알림 일괄 삭제
    void deleteByUserId(Long userId);
}
