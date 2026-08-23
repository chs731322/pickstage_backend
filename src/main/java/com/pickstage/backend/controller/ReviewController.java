package com.pickstage.backend.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.pickstage.backend.dto.MyReviewResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.ReviewUpdateRequest;
import com.pickstage.backend.dto.ReviewRequest;
import com.pickstage.backend.service.ReviewService;
import com.pickstage.backend.global.response.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // 내가 작성한 후기 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MyReviewResponse>>> getMyReviews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<MyReviewResponse> response =
                reviewService.getMyReviews(
                        userId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 후기 작성
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createVenueReview(
            @AuthenticationPrincipal Long userId,
            @RequestBody ReviewRequest request) {

        reviewService.createVenueReview(
                userId,
                request.getVenueId(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "후기가 작성되었습니다."
                        )
                );
    }

    // 후기 수정
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> updateVenueReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId,
            @RequestBody ReviewUpdateRequest request) {

        reviewService.updateVenueReview(
                userId,
                reviewId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "후기가 수정되었습니다."
                )
        );
    }

    // 후기 삭제
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteVenueReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId) {

        reviewService.deleteVenueReview(
                userId,
                reviewId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "후기가 삭제되었습니다."
                )
        );
    }
}