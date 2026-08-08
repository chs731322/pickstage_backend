package com.pickstage.backend.controller;

import com.pickstage.backend.dto.FestivalCalendarResponse;
import com.pickstage.backend.dto.FestivalDetailResponse;
import com.pickstage.backend.dto.FestivalListResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.FestivalService;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/festivals")
public class FestivalController {

    private final FestivalService festivalService;

    public FestivalController(FestivalService festivalService) {
        this.festivalService = festivalService;
    }

    // 공연 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FestivalListResponse>>> getFestivals(
            @RequestParam(required = false) String region, @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        Page<FestivalListResponse> result = festivalService.getFestivals(region, genre, keyword, sort, page, size);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
    }

    // 공연 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalDetailResponse>> getFestival(@PathVariable Long id) {

        FestivalDetailResponse result = festivalService.getFestival(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 공연 달력 조회
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<FestivalCalendarResponse>>> getFestivalCalendar(@RequestParam int year, @RequestParam int month) {

        List<FestivalCalendarResponse> result = festivalService.getCalendar(year, month);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
