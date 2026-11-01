package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash;
    private String nickname;
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProvider socialProvider;

    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // 비밀번호 변경
    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }

    // 내 정보 수정
    public void updateProfile(
            String nickname,
            String profileImage) {

        if (nickname != null) {
            this.nickname = nickname;
        }

        if (profileImage != null) {
            this.profileImage = profileImage;
        }

        this.updatedAt = LocalDateTime.now();
    }

    // 회원 탈퇴
    // 개인 식별 정보를 지우고 계정을 비활성화한다.
    // email은 UNIQUE NOT NULL이라 id로 만든 값으로 바꾼다 (같은 이메일로 다시 가입할 수 있게 된다).
    // socialId는 지우지 않는다 — 소셜 로그인은 socialId로 기존 회원을 찾기 때문에,
    // 여기서 지워버리면 탈퇴한 계정을 다시 찾을 수 없어서 "탈퇴한 회원입니다" 차단이 무력화된다.
    public void withdraw() {
        LocalDateTime now = LocalDateTime.now();
        this.email = "withdrawn-" + this.id + "@deleted.invalid";
        this.nickname = "탈퇴한 회원";
        this.passwordHash = null;
        this.profileImage = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public enum SocialProvider {
        none, kakao, google, naver
    }

    public enum Role {
        user, admin
    }

    // 회원 권한 변경
    public void updateRole(
            Role role) {
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }
}