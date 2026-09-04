package com.pickstage.backend.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import com.pickstage.backend.service.FAQService;
import com.pickstage.backend.dto.FAQDetailResponse;
import com.pickstage.backend.dto.FAQListResponse;
import com.pickstage.backend.global.response.ApiResponse;

import java.util.List;

import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/faqs")
public class FAQController {

    private final FAQService faqService;

    public FAQController(FAQService faqService) {
        this.faqService = faqService;
    }

    // FAQ 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FAQListResponse>>> getFAQs() {
        List<FAQListResponse> response = faqService.getFAQs();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // FAQ 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FAQDetailResponse>> getFAQ(@PathVariable Long id) {
        FAQDetailResponse response = faqService.getFAQ(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
