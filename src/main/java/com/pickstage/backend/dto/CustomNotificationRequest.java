package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CustomNotificationRequest {
    private Long festivalId;
    // 알림을 받고 싶은 시각 (예: 2026-12-30T18:00)
    private LocalDateTime scheduledAt;
    // 알림에 같이 보여줄 메모 (선택, 50자 이내)
    private String message;
}
