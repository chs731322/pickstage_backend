package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.NoticeDetailResponse;
import com.pickstage.backend.dto.NoticeListResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.NoticeService;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    // 공지사항 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NoticeListResponse>>> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        PageResponse<NoticeListResponse> response = noticeService.getNotices(page, size, keyword);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 공지사항 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> getNotice(@PathVariable Long id) {
        NoticeDetailResponse response = noticeService.getNotice(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
