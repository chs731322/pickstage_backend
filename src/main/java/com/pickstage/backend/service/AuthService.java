package com.pickstage.backend.service;

import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.dto.SignupRequest;
import com.pickstage.backend.entity.RefreshToken;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.global.config.JwtProvider;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtProvider jwtProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    // 회원가입
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .socialProvider(User.SocialProvider.none)
                .role(User.Role.user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        return generateTokens(user);
    }

    // 로그인
    @Transactional
    public AuthResponse signin(SigninRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        return generateTokens(user);
    }

    // 로그아웃
    @Transactional
    public void signout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // 토큰 재발급
    @Transactional
    public AuthResponse reissue(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        RefreshToken savedToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("로그인이 필요합니다."));

        if (!savedToken.getToken().equals(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }

        return generateTokens(user);
    }

    // 토큰 발급 공통 메서드
    private AuthResponse generateTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getRole().name());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.findByUserId(user.getId())
                .ifPresent(t -> refreshTokenRepository.delete(t));

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiredAt(LocalDateTime.now().plusDays(14))
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(token);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .profileImage(user.getProfileImage())
                        .role(user.getRole().name())
                        .build())
                .build();
    }
}