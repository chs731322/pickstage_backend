package com.pickstage.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.PasswordResetToken;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    // 재설정 토큰으로 조회
    Optional<PasswordResetToken> findByToken(String token);

    // 회원 탈퇴 시 해당 사용자의 비밀번호 재설정 토큰 삭제
    void deleteByUserId(Long userId);
}
