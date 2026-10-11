package com.pickstage.backend.repository;

import com.pickstage.backend.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 유저 찾기 (로그인, 중복확인)
    Optional<User> findByEmail(String email);

    // 토큰의 사용자가 탈퇴하지 않은 계정인지 확인 (JWT 필터에서 사용)
    boolean existsByIdAndDeletedAtIsNull(Long id);

    // 이메일 존재 여부 확인 (중복확인)
    boolean existsByEmail(String email);

    // 닉네임 존재 여부 확인 (중복확인)
    boolean existsByNickname(String nickname);

    // 회원 목록 조회
    @Query("SELECT u FROM User u WHERE " +
                        "(:role IS NULL OR u.role = :role) AND " +
                        "(:keyword IS NULL OR u.email LIKE %:keyword% OR u.nickname LIKE %:keyword%)")
        Page<User> findByFilters(
                        @Param("keyword") String keyword,
                        @Param("role") User.Role role,
                        Pageable pageable);

    // 소셜 로그인 사용자 찾기
    Optional<User> findBySocialProviderAndSocialId(
            User.SocialProvider socialProvider,
            String socialId
    );
}