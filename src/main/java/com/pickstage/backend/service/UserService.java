package com.pickstage.backend.service;

import com.pickstage.backend.dto.MyPageResponse;
import com.pickstage.backend.dto.MyPageUpdateRequest;
import com.pickstage.backend.dto.PasswordChangeRequest;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 내 정보 조회
    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        return MyPageResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // 내 정보 수정
    @Transactional
    public MyPageResponse updateMyPage(
            Long userId,
            MyPageUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 닉네임이 전달된 경우에만 중복 확인 하게끔
        if (request.getNickname() != null
                && !request.getNickname()
                        .equals(user.getNickname())
                && userRepository.existsByNickname(
                        request.getNickname())) {

            throw new IllegalArgumentException(
                    "이미 사용 중인 닉네임입니다."
            );
        }

        // 닉네임 및 프로필 이미지 수정
        user.updateProfile(
                request.getNickname(),
                request.getProfileImage()
        );

        return MyPageResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // 비밀번호 변경
    @Transactional
    public void changePassword(
            Long userId,
            PasswordChangeRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 소셜 로그인 사용자는 비밀번호 변경 불가
        if (user.getSocialProvider()
                != User.SocialProvider.none) {

            throw new IllegalArgumentException(
                    "소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다."
            );
        }

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "현재 비밀번호가 일치하지 않습니다."
            );
        }

        // 새 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(
                        request.getNewPassword()
                );

        // 비밀번호 변경
        user.updatePassword(encodedPassword);
    }

    // 회원 탈퇴
    @Transactional
    public void withdraw(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 소프트 삭제 처리
        user.withdraw();

        // Refresh Token 삭제
        refreshTokenRepository.deleteByUserId(userId);
    }
}