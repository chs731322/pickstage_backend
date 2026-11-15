package com.pickstage.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 같은 사람이 같은 대상을 또 신고하는지 확인
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, Report.TargetType targetType, Long targetId);

    // 아직 처리하지 않은 신고 수 (같은 사람은 한 대상을 한 번만 신고할 수 있어서, 곧 신고한 사람 수와 같다)
    long countByTargetTypeAndTargetIdAndStatus(Report.TargetType targetType, Long targetId, Report.Status status);
}
