package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 앱이 알려준 기기의 푸시 토큰 (Expo 푸시 토큰, 예: ExponentPushToken[xxxx]).
// 토큰은 기기(앱 설치) 하나에 하나라서 유일하고, 다른 계정으로 로그인하면 그 계정의 것으로 옮겨진다.
@Entity
@Table(name = "push_tokens")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String token;

    // ios / android
    private String platform;

    // 이 기기로 푸시를 받을지 (앱의 "푸시 알림" 켜기·끄기)
    private boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 같은 기기에 다른 계정이 로그인했을 때: 새 계정의 것으로 옮기고 다시 켠다
    public void reassignTo(Long userId, String platform) {
        this.userId = userId;
        this.platform = platform;
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void touch(String platform) {
        this.platform = platform;
        this.updatedAt = LocalDateTime.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
