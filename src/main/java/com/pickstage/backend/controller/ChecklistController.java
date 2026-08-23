package com.pickstage.backend.controller;

import com.pickstage.backend.dto.ChecklistAddRequest;
import com.pickstage.backend.dto.ChecklistResponse;
import com.pickstage.backend.dto.ChecklistUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ChecklistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checklists")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    // 체크리스트 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChecklistResponse>>> getChecklist(
            @AuthenticationPrincipal Long userId) {

        List<ChecklistResponse> result = checklistService.getChecklistByUserId(userId).stream()
                .map(ChecklistResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 체크리스트 항목 추가
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addChecklistItem(
            @RequestBody ChecklistAddRequest request, @AuthenticationPrincipal Long userId) {

        checklistService.addChecklistItem(userId, request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("체크리스트 항목이 추가되었습니다."));
    }

    // 체크리스트 수정 (내용/체크 여부)
    @PatchMapping("/{checklistId}")
    public ResponseEntity<ApiResponse<Void>> updateChecklistItem(
            @PathVariable Long checklistId,
            @RequestBody ChecklistUpdateRequest request,
            @AuthenticationPrincipal Long userId) {

        checklistService.updateChecklistItem(
                checklistId, userId, request.getContent(), request.getIsChecked());
        return ResponseEntity.ok(ApiResponse.success("체크리스트가 수정되었습니다."));
    }

    // 체크리스트 항목 삭제
    @DeleteMapping("/{checklistId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklistItem(
            @PathVariable Long checklistId, @AuthenticationPrincipal Long userId) {

        checklistService.deleteChecklistItem(checklistId, userId);
        return ResponseEntity.ok(ApiResponse.success("체크리스트 항목이 삭제되었습니다."));
    }
}
