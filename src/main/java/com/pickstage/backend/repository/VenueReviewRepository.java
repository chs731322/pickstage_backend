package com.pickstage.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pickstage.backend.entity.VenueReview;

@Repository
public interface VenueReviewRepository extends JpaRepository<VenueReview, Long> {

    List<VenueReview> findByVenueIdAndDeletedAtIsNull(Long venueId);

    // 내가 작성한 후기 목록 조회
    Page<VenueReview> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );
}