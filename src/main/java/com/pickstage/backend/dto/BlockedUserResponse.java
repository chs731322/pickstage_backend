package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BlockedUserResponse {
    private Long userId;
    private String nickname;
    private String profileImage;
    private LocalDateTime blockedAt;
}
