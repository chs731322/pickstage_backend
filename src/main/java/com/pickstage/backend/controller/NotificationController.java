package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.NotificationResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    // GET /api/notifications, PATCH /api/notifications/{id}/read

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationResponse response = notificationService.getNotifications(userId, isRead, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success("알림을 읽음 처리했습니다."));
    }
}