package com.pickstage.backend.controller;

import com.pickstage.backend.dto.ConcertDetailResponse;
import com.pickstage.backend.dto.ConcertListResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ConcertService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/concerts")
public class ConcertController {

	private final ConcertService concertService;

	public ConcertController(ConcertService concertService) {
		this.concertService = concertService;
	}

	// 공연 목록 조회
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<ConcertListResponse>>> getConcerts(
			@RequestParam(required = false) String region, @RequestParam(required = false) String genre,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

		Page<ConcertListResponse> result = concertService.getConcerts(region, genre, keyword, sort, page, size);

		return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
	}

	// 공연 상세 조회
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ConcertDetailResponse>> getConcert(@PathVariable Long id) {

		ConcertDetailResponse result = concertService.getConcert(id);
		return ResponseEntity.ok(ApiResponse.success(result));
	}
}