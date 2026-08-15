package com.pickstage.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.PasswordResetToken;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    // 재설정 토큰으로 조회
    Optional<PasswordResetToken> findByToken(String token);
}