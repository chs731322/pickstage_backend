package com.pickstage.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.ReportCreateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 게시글·댓글·공연장 후기·사용자 신고
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createReport(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody ReportCreateRequest request) {
        reportService.createReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("신고가 접수되었습니다."));
    }
}
