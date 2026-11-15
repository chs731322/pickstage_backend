package com.pickstage.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.pickstage.backend.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 같은 사람이 같은 대상을 또 신고하는지 확인
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, Report.TargetType targetType, Long targetId);

    // 아직 처리하지 않은 신고 수 (같은 사람은 한 대상을 한 번만 신고할 수 있어서, 곧 신고한 사람 수와 같다)
    long countByTargetTypeAndTargetIdAndStatus(Report.TargetType targetType, Long targetId, Report.Status status);

    // 관리자 화면용: 같은 대상에 쌓인 신고를 하나로 묶어 [대상 종류, 대상 id, 신고 수, 마지막 신고 시각]으로 돌려준다 (최근 신고가 먼저)
    @Query("SELECT r.targetType, r.targetId, COUNT(r), MAX(r.createdAt) FROM Report r " +
            "WHERE r.status IN :statuses GROUP BY r.targetType, r.targetId ORDER BY MAX(r.createdAt) DESC")
    List<Object[]> findGroups(Collection<Report.Status> statuses, Pageable pageable);

    List<Report> findByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(
            Report.TargetType targetType, Long targetId, Collection<Report.Status> statuses);

    // 한 대상에 쌓인 처리 전 신고를 한꺼번에 처리 완료로 바꾼다. 바꾼 개수를 돌려준다.
    @Modifying
    @Query("UPDATE Report r SET r.status = :newStatus, r.handledAt = :handledAt, r.handledBy = :handledBy " +
            "WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.status = :oldStatus")
    int markHandled(Report.TargetType targetType, Long targetId, Report.Status oldStatus,
            Report.Status newStatus, LocalDateTime handledAt, Long handledBy);
}
