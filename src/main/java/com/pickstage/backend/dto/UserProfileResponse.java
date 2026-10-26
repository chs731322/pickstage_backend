package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

// 다른 사용자에게 보이는 프로필. 이메일 같은 개인 정보는 담지 않는다.
@Getter
@Builder
public class UserProfileResponse {

    private Long id;
    private String nickname;
    private String profileImage;
    private LocalDateTime createdAt;
    private boolean self;
    // 보는 사람 기준으로 볼 수 있는 항목 (본인이면 모두 true)
    private PrivacySettings visible;
}
