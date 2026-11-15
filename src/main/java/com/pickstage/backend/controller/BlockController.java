package com.pickstage.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.BlockedUserResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.BlockService;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    // 내가 차단한 사용자 목록
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getBlockedUsers(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(blockService.getBlockedUsers(userId)));
    }

    // 사용자 차단
    @PostMapping("/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal Long userId, @PathVariable Long targetUserId) {
        blockService.block(userId, targetUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("차단했습니다."));
    }

    // 차단 해제
    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal Long userId, @PathVariable Long targetUserId) {
        blockService.unblock(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("차단을 해제했습니다."));
    }
}
