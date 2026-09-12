package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.ReviewHighlightResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ReviewHighlightService;

@RestController
public class ReviewHighlightController {
    private final ReviewHighlightService reviewHighlightService;

    public ReviewHighlightController(ReviewHighlightService reviewHighlightService) {
        this.reviewHighlightService = reviewHighlightService;
    }

    private static final String INSUFFICIENT_MESSAGE = "아직 하이라이트를 만들 만큼 후기가 쌓이지 않았어요.";

    // 공연 후기 하이라이트 조회
    @GetMapping("/api/festivals/{festivalId}/review-highlights")
    public ResponseEntity<ApiResponse<ReviewHighlightResponse>> getFestivalHighlights(
            @PathVariable Long festivalId) {
        ReviewHighlightResponse result = reviewHighlightService.getFestivalHighlights(festivalId);
        String message = result.getKeywords().isEmpty() ? INSUFFICIENT_MESSAGE : null;
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    // 공연장 후기 하이라이트 조회
    @GetMapping("/api/venues/{venueId}/review-highlights")
    public ResponseEntity<ApiResponse<ReviewHighlightResponse>> getVenueHighlights(
            @PathVariable Long venueId) {
        ReviewHighlightResponse result = reviewHighlightService.getVenueHighlights(venueId);
        String message = result.getKeywords().isEmpty() ? INSUFFICIENT_MESSAGE : null;
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }
}
