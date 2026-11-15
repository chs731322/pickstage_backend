package com.pickstage.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.UserBlock;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 차단 해제
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 내가 차단한 사람 목록 (최근에 차단한 순)
    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);
}
