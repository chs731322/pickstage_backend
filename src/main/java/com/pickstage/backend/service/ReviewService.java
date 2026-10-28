package com.pickstage.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.MyReviewResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.ReviewUpdateRequest;
import com.pickstage.backend.dto.ReviewRequest;
import com.pickstage.backend.entity.ReviewImage;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.Venue;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.repository.VenueReviewRepository;
import com.pickstage.backend.repository.ReviewImageRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueRepository;

@Service
public class ReviewService {

        private final VenueReviewRepository venueReviewRepository;
        private final VenueRepository venueRepository;
        private final ReviewImageRepository reviewImageRepository;
        private final UserRepository userRepository;
        private final UploadedImagePolicy uploadedImagePolicy;

        private static final int IMAGES_MAX_COUNT = 5;

        public ReviewService(VenueReviewRepository venueReviewRepository, VenueRepository venueRepository,
                        ReviewImageRepository reviewImageRepository, UserRepository userRepository,
                        UploadedImagePolicy uploadedImagePolicy) {
                this.venueReviewRepository = venueReviewRepository;
                this.venueRepository = venueRepository;
                this.reviewImageRepository = reviewImageRepository;
                this.userRepository = userRepository;
                this.uploadedImagePolicy = uploadedImagePolicy;
        }

        // 후기 사진은 우리 저장소에 올린 이미지만, 최대 5장까지 허용한다
        private void validateImages(java.util.List<String> images) {
                if (images == null) {
                        return;
                }
                if (images.size() > IMAGES_MAX_COUNT) {
                        throw new IllegalArgumentException("사진은 최대 " + IMAGES_MAX_COUNT + "장까지 올릴 수 있습니다.");
                }
                for (String url : images) {
                        if (!uploadedImagePolicy.isUploadedImageUrl(url)) {
                                throw new IllegalArgumentException("업로드한 이미지만 사용할 수 있습니다.");
                        }
                }
        }

        // 내가 작성한 후기 목록 조회
        @Transactional(readOnly = true)
        public PageResponse<MyReviewResponse> getMyReviews(
                        Long userId,
                        int page,
                        int size) {

                Pageable pageable = PageRequest.of(page, size);

                Page<VenueReview> reviews = venueReviewRepository
                                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                                                userId,
                                                pageable);

                Page<MyReviewResponse> responsePage = reviews.map(review -> {

                        Venue venue = venueRepository.findById(
                                        review.getVenueId())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "존재하지 않는 공연장입니다."));

                        return MyReviewResponse.builder()
                                        .id(review.getId())
                                        .venueName(venue.getName())
                                        .rating(review.getRating())
                                        .content(review.getContent())
                                        .viewInfo(review.getViewInfo())
                                        .standingTip(review.getStandingTip())
                                        .seatSection(review.getSeatSection())
                                        .createdAt(review.getCreatedAt())
                                        .images(reviewImageRepository.findByReviewIdOrderBySortOrderAsc(review.getId()).stream()
                                                        .map(ReviewImage::getImageUrl)
                                                        .toList())
                                        .build();
                });

                return PageResponse.of(responsePage);
        }

        // 후기 작성
        @Transactional
        public void createVenueReview(
                        Long userId,
                        Long venueId,
                        ReviewRequest request) {

                if (!venueRepository.existsById(request.getVenueId())) {
                        throw new IllegalStateException(
                                        "존재하지 않는 공연장입니다.");
                }

                if (request.getRating() == null) {
                        throw new IllegalArgumentException(
                                        "올바르지 않은 요청입니다.");
                }
                validateImages(request.getImages());

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

                                ReviewImage reviewImage = new ReviewImage(
                                                null,
                                                review.getId(),
                                                url,
                                                i);

                                reviewImageRepository.save(reviewImage);
                        }
                }
        }

        // 후기 수정
        @Transactional
        public void updateVenueReview(
                        Long userId,
                        Long reviewId,
                        ReviewUpdateRequest request) {

                VenueReview review = findVisibleReview(reviewId);

                if (!review.getUserId().equals(userId)) {
                        throw new AccessDeniedException(
                                        "수정 권한이 없습니다.");
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
                        review.setStandingTip(
                                        request.getStandingTip());
                }

                if (request.getSeatSection() != null) {
                        review.setSeatSection(
                                        request.getSeatSection());
                }

                validateImages(request.getImages());
                if (request.getImages() != null) {

                        reviewImageRepository.deleteByReviewId(
                                        reviewId);

                        for (int i = 0; i < request.getImages().size(); i++) {

                                String url = request.getImages().get(i);

                                ReviewImage reviewImage = new ReviewImage(
                                                null,
                                                reviewId,
                                                url,
                                                i);

                                reviewImageRepository.save(
                                                reviewImage);
                        }
                }

                venueReviewRepository.save(review);
        }

        // 후기 삭제
        @Transactional
        public void deleteVenueReview(
                        Long userId,
                        Long reviewId) {

                VenueReview review = findVisibleReview(reviewId);

                if (!review.getUserId().equals(userId)) {
                        throw new AccessDeniedException(
                                        "삭제 권한이 없습니다.");
                }

                review.setDeletedAt(
                                java.time.LocalDateTime.now());

                venueReviewRepository.save(review);
        }

        // 삭제되지 않은 후기 조회
        private VenueReview findVisibleReview(Long reviewId) {
                VenueReview review = venueReviewRepository.findById(reviewId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "존재하지 않는 후기입니다."));

                if (review.getDeletedAt() != null) {
                        throw new IllegalStateException(
                                        "존재하지 않는 후기입니다.");
                }

                return review;
        }

        @Transactional
        public void forceDeleteReview(
                        Long userId,
                        Long reviewId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

                if (!user.getRole().equals(User.Role.admin)) {
                        throw new AccessDeniedException("관리자 권한이 없습니다.");
                }

                VenueReview review = venueReviewRepository.findById(reviewId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "존재하지 않는 후기입니다."));

                review.setDeletedAt(
                                java.time.LocalDateTime.now());

                venueReviewRepository.save(review);
        }
}
