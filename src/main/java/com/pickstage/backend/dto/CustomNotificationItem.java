package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomNotificationItem {
    private Long id;
    private Long festivalId;
    private LocalDateTime scheduledAt;
    private String message;
}
