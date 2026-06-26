package com.pickstage.backend.repository;

import com.pickstage.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 유저 찾기 (로그인, 중복확인)
    Optional<User> findByEmail(String email);

    // 이메일 존재 여부 확인 (중복확인)
    boolean existsByEmail(String email);

    // 닉네임 존재 여부 확인 (중복확인)
    boolean existsByNickname(String nickname);
}