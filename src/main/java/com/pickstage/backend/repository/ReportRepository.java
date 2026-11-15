package com.pickstage.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 같은 사람이 같은 대상을 또 신고하는지 확인
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, Report.TargetType targetType, Long targetId);
}
