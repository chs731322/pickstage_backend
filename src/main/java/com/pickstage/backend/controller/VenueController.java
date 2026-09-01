package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.VenueDetailResponse;
import com.pickstage.backend.dto.VenueListResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.VenueService;


@RestController
@RequestMapping("/api/venues")
public class VenueController {
    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    // 공연장 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VenueListResponse>>> getVenues(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<VenueListResponse> venues = venueService.getVenues(region, keyword, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(venues));
    }

    // 공연장 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VenueDetailResponse>> getVenueDetail(@PathVariable Long id) {
        VenueDetailResponse venueDetail = venueService.getVenueDetail(id);
        return ResponseEntity.ok(ApiResponse.success(venueDetail));
    }

}
