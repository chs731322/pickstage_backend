package com.pickstage.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pickstage.backend.entity.ReviewImage;

@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewIdOrderBySortOrderAsc(Long reviewId);

    void deleteByReviewId(Long reviewId);
}