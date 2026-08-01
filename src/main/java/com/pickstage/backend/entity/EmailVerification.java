package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 6)
    private String verifyCode;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false)
    private boolean verified;

    @Builder
    public EmailVerification(
            String email,
            String verifyCode,
            LocalDateTime expiredAt,
            boolean verified) {

        this.email = email;
        this.verifyCode = verifyCode;
        this.expiredAt = expiredAt;
        this.verified = verified;
    }

    // 인증코드 재발급
    public void updateVerificationCode(
            String verifyCode,
            LocalDateTime expiredAt) {

        this.verifyCode = verifyCode;
        this.expiredAt = expiredAt;
        this.verified = false;
    }

    // 이메일 인증 완료 처리
    public void completeVerification() {
        this.verified = true;
    }

    // 인증코드 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }
}