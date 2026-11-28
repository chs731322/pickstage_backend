package com.pickstage.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.WebPushService;

// 관리자 브라우저 알림 구독
@RestController
@RequestMapping("/api/admin/web-push")
public class AdminWebPushController {

    private final WebPushService webPushService;

    public AdminWebPushController(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    // 브라우저의 PushSubscription.toJSON() 모양
    public record SubscriptionRequest(String endpoint, Map<String, String> keys) {
    }

    @GetMapping("/key")
    public ResponseEntity<ApiResponse<String>> publicKey(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(webPushService.publicKey(userId)));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(@AuthenticationPrincipal Long userId, @RequestBody SubscriptionRequest request) {
        Map<String, String> keys = request.keys() == null ? Map.of() : request.keys();
        webPushService.subscribe(userId, request.endpoint(), keys.get("p256dh"), keys.get("auth"));
        return ResponseEntity.ok(ApiResponse.success("이 브라우저에서 알림을 받아요."));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@AuthenticationPrincipal Long userId, @RequestBody SubscriptionRequest request) {
        webPushService.unsubscribe(userId, request.endpoint());
        return ResponseEntity.ok(ApiResponse.success("알림을 껐어요."));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Integer>> test(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(webPushService.sendTest(userId)));
    }
}
