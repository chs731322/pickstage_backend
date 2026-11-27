package com.pickstage.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.FestivalDraftRequest;
import com.pickstage.backend.dto.FestivalUpdateSuggestionResponse;
import com.pickstage.backend.entity.FestivalUpdateSuggestion;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.FestivalUpdateSuggestionService;

// 관리자: 공연 정보 갱신 제안 (공지 붙여넣기 -> 새로 생긴 것만 제안 -> 승인하면 반영)
@RestController
@RequestMapping("/api/admin")
public class AdminFestivalUpdateController {

    private final FestivalUpdateSuggestionService suggestionService;

    public AdminFestivalUpdateController(FestivalUpdateSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping("/festivals/{festivalId}/update-suggestions")
    public ResponseEntity<ApiResponse<List<FestivalUpdateSuggestionResponse>>> createSuggestions(
            @AuthenticationPrincipal Long userId, @PathVariable Long festivalId, @RequestBody FestivalDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.success(suggestionService.createSuggestions(userId, festivalId, request)));
    }

    @GetMapping("/update-suggestions")
    public ResponseEntity<ApiResponse<List<FestivalUpdateSuggestionResponse>>> getSuggestions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "PENDING") FestivalUpdateSuggestion.Status status) {
        return ResponseEntity.ok(ApiResponse.success(suggestionService.getSuggestions(userId, status)));
    }

    @PostMapping("/update-suggestions/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        suggestionService.approve(userId, id);
        return ResponseEntity.ok(ApiResponse.success("공연에 반영했어요."));
    }

    @PostMapping("/update-suggestions/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        suggestionService.reject(userId, id);
        return ResponseEntity.ok(ApiResponse.success("제안을 거절했어요."));
    }
}
