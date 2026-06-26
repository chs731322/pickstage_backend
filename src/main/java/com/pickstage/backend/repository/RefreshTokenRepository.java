package com.pickstage.backend.repository;

import com.pickstage.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 유저로 토큰 찾기
    Optional<RefreshToken> findByUserId(Long userId);

    // 토큰값으로 찾기
    Optional<RefreshToken> findByToken(String token);

    // 유저 토큰 삭제 (로그아웃)
    void deleteByUserId(Long userId);
}