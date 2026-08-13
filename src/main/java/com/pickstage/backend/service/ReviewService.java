package com.pickstage.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.ReviewUpdateRequest;
import com.pickstage.backend.dto.ReviewRequest;
import com.pickstage.backend.entity.ReviewImage;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.repository.VenueReviewRepository;
import com.pickstage.backend.repository.ReviewImageRepository;
import com.pickstage.backend.repository.VenueRepository;

@Service
public class ReviewService {
    private final VenueReviewRepository venueReviewRepository;
    private final VenueRepository venueRepository;
    private final ReviewImageRepository reviewImageRepository;

    public ReviewService(VenueReviewRepository venueReviewRepository, VenueRepository venueRepository,
            ReviewImageRepository reviewImageRepository) {
        this.venueReviewRepository = venueReviewRepository;
        this.venueRepository = venueRepository;
        this.reviewImageRepository = reviewImageRepository;
    }

    // 후기 작성
    @Transactional
    public void createVenueReview(Long userId, Long venueId, ReviewRequest request) {
        if (!venueRepository.existsById(request.getVenueId())) {
            throw new IllegalStateException("존재하지 않는 공연장입니다.");
        }
        if (request.getRating() == null) {
            throw new IllegalArgumentException("올바르지 않은 요청입니다.");
        }
        VenueReview review = VenueReview.builder()
                .userId(userId)
                .venueId(venueId)
                .rating(request.getRating())
                .content(request.getContent())
                .viewInfo(request.getViewInfo())
                .standingTip(request.getStandingTip())
                .seatSection(request.getSeatSection())
                .createdAt(java.time.LocalDateTime.now())
                .build();

        venueReviewRepository.save(review);

        if (request.getImages() != null) {
            for (int i = 0; i < request.getImages().size(); i++) {
                String url = request.getImages().get(i);
                ReviewImage reviewImage = new ReviewImage(null, review.getId(), url, i);
                reviewImageRepository.save(reviewImage);
            }
        }
    }

    // 후기 수정
    @Transactional
    public void updateVenueReview(Long userId, Long reviewId, ReviewUpdateRequest request) {
        VenueReview review = venueReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 후기입니다."));

        if (!review.getUserId().equals(userId)) {
            throw new AccessDeniedException("수정 권한이 없습니다.");
        }

        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getContent() != null) {
            review.setContent(request.getContent());
        }
        if (request.getViewInfo() != null) {
            review.setViewInfo(request.getViewInfo());
        }
        if (request.getStandingTip() != null) {
            review.setStandingTip(request.getStandingTip());
        }
        if (request.getSeatSection() != null) {
            review.setSeatSection(request.getSeatSection());
        }


        if (request.getImages() != null) {
            reviewImageRepository.deleteByReviewId(reviewId);
            for (int i = 0; i < request.getImages().size(); i++) {
                String url = request.getImages().get(i);
                ReviewImage reviewImage = new ReviewImage(null, reviewId, url, i);
                reviewImageRepository.save(reviewImage);
            }
        }

        venueReviewRepository.save(review);
    }

    // 후기 삭제
    @Transactional
    public void deleteVenueReview(Long userId, Long reviewId) {
        VenueReview review = venueReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 후기입니다."));

        if (!review.getUserId().equals(userId)) {
            throw new AccessDeniedException("삭제 권한이 없습니다.");
        }

        review.setDeletedAt(java.time.LocalDateTime.now());
        venueReviewRepository.save(review);
    }

}
