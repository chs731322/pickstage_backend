package com.pickstage.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.PushTokenRequest;
import com.pickstage.backend.dto.PushTokenResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.PushService;

import jakarta.validation.Valid;

// 앱 푸시 알림용 기기 토큰 API (모두 로그인 필요)
@RestController
@RequestMapping("/api/push-tokens")
public class PushTokenController {

    private final PushService pushService;

    public PushTokenController(PushService pushService) {
        this.pushService = pushService;
    }

    // 기기 등록·갱신 (앱이 켜질 때). 응답의 enabled는 이 기기의 푸시 켜짐 상태
    @PostMapping
    public ResponseEntity<ApiResponse<PushTokenResponse>> register(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody PushTokenRequest request) {
        PushTokenResponse response = pushService.register(userId, request.getToken(), request.getPlatform());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "기기를 등록했습니다."));
    }

    // 이 기기로 푸시를 받을지 켜고 끄기
    @PatchMapping
    public ResponseEntity<ApiResponse<PushTokenResponse>> setEnabled(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody PushTokenRequest request) {
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("켜기·끄기 값(enabled)이 필요합니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(
                pushService.setEnabled(userId, request.getToken(), request.getEnabled())));
    }

    // 기기 등록 해제 (로그아웃할 때)
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody PushTokenRequest request) {
        pushService.unregister(userId, request.getToken());
        return ResponseEntity.ok(ApiResponse.success("기기 등록을 해제했습니다."));
    }

    // 내 기기로 테스트 푸시 보내기 (1분에 한 번)
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sendTest(@AuthenticationPrincipal Long userId) {
        int sent = pushService.sendTest(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("sent", sent), "테스트 알림을 보냈어요."));
    }
}
