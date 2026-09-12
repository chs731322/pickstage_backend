package com.pickstage.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.ReviewHighlightCache;

public interface ReviewHighlightCacheRepository extends JpaRepository<ReviewHighlightCache, Long> {

    Optional<ReviewHighlightCache> findByTargetTypeAndTargetId(
            ReviewHighlightCache.TargetType targetType, Long targetId);
}
