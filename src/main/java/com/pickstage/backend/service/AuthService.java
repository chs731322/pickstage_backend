package com.pickstage.backend.service;

import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.dto.KakaoUserResponse;
import com.pickstage.backend.dto.NaverUserResponse;
import com.pickstage.backend.dto.PasswordResetRequest;
import com.pickstage.backend.dto.ReissueResponse;
import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.dto.SignupRequest;
import com.pickstage.backend.entity.PasswordResetToken;
import com.pickstage.backend.entity.RefreshToken;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.global.config.JwtProvider;
import com.pickstage.backend.repository.PasswordResetTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int PASSWORD_RESET_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final KakaoService kakaoService;
    private final NaverService naverService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       JwtProvider jwtProvider,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       KakaoService kakaoService,
                       NaverService naverService) {

        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.kakaoService = kakaoService;
        this.naverService = naverService;
    }

    // 이메일 중복 확인
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    // 닉네임 중복 확인
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
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

        // 이메일 인증 완료 여부 확인
        if (!emailService.isEmailVerified(request.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
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

        // 회원가입 완료 후 이메일 인증정보 삭제
        emailService.deleteVerification(request.getEmail());

        return generateTokens(user);
    }

    // 로그인
    @Transactional
    public AuthResponse signin(SigninRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "이메일 또는 비밀번호가 일치하지 않습니다."
                        )
                );

        // 탈퇴한 사용자는 로그인 불가
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "이메일 또는 비밀번호가 일치하지 않습니다."
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "이메일 또는 비밀번호가 일치하지 않습니다."
            );
        }

        return generateTokens(user);
    }

    // 카카오 로그인
    @Transactional
    public AuthResponse kakaoLogin(String code) {

        // 인가코드로 카카오 Access Token 발급
        String kakaoAccessToken =
                kakaoService.getAccessToken(code);

        // 카카오 사용자 정보 조회
        KakaoUserResponse kakaoUser =
                kakaoService.getUserInfo(kakaoAccessToken);

        String socialId =
                String.valueOf(kakaoUser.getId());

        // 기존 카카오 회원인지 확인
        User existingUser =
                userRepository
                        .findBySocialProviderAndSocialId(
                                User.SocialProvider.kakao,
                                socialId
                        )
                        .orElse(null);

        if (existingUser != null) {

            // 탈퇴한 사용자는 로그인 불가
            if (existingUser.getDeletedAt() != null) {
                throw new IllegalArgumentException(
                        "탈퇴한 회원입니다."
                );
            }

            return generateTokens(existingUser);
        }

        // 신규 카카오 회원 정보
        KakaoUserResponse.KakaoAccount kakaoAccount =
                kakaoUser.getKakaoAccount();

        if (kakaoAccount == null
                || kakaoAccount.getEmail() == null
                || kakaoAccount.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "카카오 계정 이메일 정보를 가져올 수 없습니다."
            );
        }

        String email = kakaoAccount.getEmail();

        String nickname = "카카오사용자" + socialId;

        String profileImage = null;

        if (kakaoAccount.getProfile() != null) {

            if (kakaoAccount.getProfile().getNickname() != null
                    && !kakaoAccount.getProfile()
                            .getNickname()
                            .isBlank()) {

                nickname =
                        kakaoAccount.getProfile()
                                .getNickname();
            }

            profileImage =
                    kakaoAccount.getProfile()
                            .getProfileImageUrl();
        }

        // 동일 닉네임이 이미 존재하면 중복되지 않도록 변경
        if (userRepository.existsByNickname(nickname)) {
            nickname = nickname + "_" + socialId;
        }

        // 동일 이메일이 이미 존재하는 경우
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 다른 방식으로 가입된 이메일입니다."
            );
        }

        // 신규 카카오 회원 자동 가입
        User newUser = User.builder()
                .email(email)
                .passwordHash(null)
                .nickname(nickname)
                .profileImage(profileImage)
                .socialProvider(User.SocialProvider.kakao)
                .socialId(socialId)
                .role(User.Role.user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(newUser);

        return generateTokens(newUser);
    }

    // 네이버 로그인
    @Transactional
    public AuthResponse naverLogin(
            String code,
            String state) {

        // 인가코드와 state로 네이버 Access Token 발급
        String naverAccessToken =
                naverService.getAccessToken(
                        code,
                        state
                );

        // 네이버 사용자 정보 조회
        NaverUserResponse naverUser =
                naverService.getUserInfo(
                        naverAccessToken
                );

        NaverUserResponse.Response naverAccount =
                naverUser.getResponse();

        String socialId =
                naverAccount.getId();

        // 기존 네이버 회원인지 확인
        User existingUser =
                userRepository
                        .findBySocialProviderAndSocialId(
                                User.SocialProvider.naver,
                                socialId
                        )
                        .orElse(null);

        if (existingUser != null) {

            // 탈퇴한 사용자는 로그인 불가
            if (existingUser.getDeletedAt() != null) {
                throw new IllegalArgumentException(
                        "탈퇴한 회원입니다."
                );
            }

            return generateTokens(existingUser);
        }

        // 이메일 정보 확인
        if (naverAccount.getEmail() == null
                || naverAccount.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "네이버 계정 이메일 정보를 가져올 수 없습니다."
            );
        }

        String email =
                naverAccount.getEmail();

        String nickname =
                "네이버사용자" + socialId;

        String profileImage = null;

        // 네이버 닉네임이 있으면 사용
        if (naverAccount.getNickname() != null
                && !naverAccount.getNickname().isBlank()) {

            nickname =
                    naverAccount.getNickname();
        }

        // 네이버 프로필 이미지가 있으면 사용
        if (naverAccount.getProfile_image() != null
                && !naverAccount.getProfile_image().isBlank()) {

            profileImage =
                    naverAccount.getProfile_image();
        }

        // 동일 닉네임이 이미 존재하면 중복되지 않도록 변경
        if (userRepository.existsByNickname(nickname)) {
            nickname =
                    nickname + "_" + socialId;
        }

        // 동일 이메일이 이미 존재하는 경우
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 다른 방식으로 가입된 이메일입니다."
            );
        }

        // 신규 네이버 회원 자동 가입
        User newUser = User.builder()
                .email(email)
                .passwordHash(null)
                .nickname(nickname)
                .profileImage(profileImage)
                .socialProvider(User.SocialProvider.naver)
                .socialId(socialId)
                .role(User.Role.user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(newUser);

        return generateTokens(newUser);
    }

    // 로그아웃
    @Transactional
    public void signout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // 토큰 재발급
    @Transactional
    public ReissueResponse reissue(String refreshToken) {

        // Refresh Token 만료 여부 확인
        if (jwtProvider.isExpired(refreshToken)) {
            throw new IllegalArgumentException(
                    "리프레시 토큰이 만료되었습니다. 다시 로그인하세요."
            );
        }

        // Refresh Token 유효성 확인
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException(
                    "유효하지 않은 토큰입니다."
            );
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "유효하지 않은 토큰입니다."
                        )
                );

        // 탈퇴한 사용자는 토큰 재발급 불가
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "유효하지 않은 토큰입니다."
            );
        }

        RefreshToken savedToken =
                refreshTokenRepository.findByUserId(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "유효하지 않은 토큰입니다."
                                )
                        );

        // DB에 저장된 Refresh Token과 비교
        if (!savedToken.getToken().equals(refreshToken)) {
            throw new IllegalArgumentException(
                    "유효하지 않은 토큰입니다."
            );
        }

        // 새로운 Access Token 생성
        String newAccessToken =
                jwtProvider.generateAccessToken(
                        user.getId(),
                        user.getRole().name()
                );

        // 새로운 Refresh Token 생성
        String newRefreshToken =
                jwtProvider.generateRefreshToken(
                        user.getId()
                );

        // 기존 Refresh Token 삭제
        refreshTokenRepository.delete(savedToken);

        // 새로운 Refresh Token 저장
        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshToken)
                .expiredAt(LocalDateTime.now().plusDays(14))
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(newToken);

        return ReissueResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    // 비밀번호 재설정 이메일 발송
    @Transactional
    public void sendPasswordResetEmail(String email) {

        // 가입된 이메일인지 확인
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "가입되지 않은 이메일입니다."
                        )
                );

        // 소셜 로그인 사용자는 비밀번호 재설정 불가
        if (user.getSocialProvider() != User.SocialProvider.none) {
            throw new IllegalArgumentException(
                    "소셜 로그인 사용자는 비밀번호를 재설정할 수 없습니다."
            );
        }

        // 비밀번호 재설정 토큰 생성
        String token = UUID.randomUUID().toString();

        LocalDateTime expiredAt =
                LocalDateTime.now()
                        .plusMinutes(
                                PASSWORD_RESET_EXPIRATION_MINUTES
                        );

        PasswordResetToken passwordResetToken =
                PasswordResetToken.builder()
                        .user(user)
                        .token(token)
                        .expiredAt(expiredAt)
                        .used(false)
                        .createdAt(LocalDateTime.now())
                        .build();

        // 재설정 토큰 저장
        passwordResetTokenRepository.save(passwordResetToken);

        // 비밀번호 재설정 링크 이메일 발송
        emailService.sendPasswordResetEmail(
                user.getEmail(),
                token
        );
    }

    // 비밀번호 재설정
    @Transactional
    public void resetPassword(PasswordResetRequest request) {

        PasswordResetToken resetToken =
                passwordResetTokenRepository
                        .findByToken(request.getToken())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "비밀번호 재설정 토큰이 유효하지 않습니다."
                                )
                        );

        // 이미 사용한 토큰인지 확인
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 토큰이 유효하지 않습니다."
            );
        }

        // 토큰 만료 여부 확인
        if (resetToken.isExpired()) {
            throw new IllegalArgumentException(
                    "만료된 토큰입니다."
            );
        }

        User user = resetToken.getUser();

        // 새 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(
                        request.getNewPassword()
                );

        // 비밀번호 변경
        user.updatePassword(encodedPassword);

        // 재설정 토큰 사용 완료 처리
        resetToken.markAsUsed();
    }

    // 토큰 발급 공통 메서드
    private AuthResponse generateTokens(User user) {

        String accessToken =
                jwtProvider.generateAccessToken(
                        user.getId(),
                        user.getRole().name()
                );

        String refreshToken =
                jwtProvider.generateRefreshToken(
                        user.getId()
                );

        refreshTokenRepository.findByUserId(user.getId())
                .ifPresent(t ->
                        refreshTokenRepository.delete(t)
                );

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
                .user(
                        AuthResponse.UserInfo.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .nickname(user.getNickname())
                                .profileImage(user.getProfileImage())
                                .role(user.getRole().name())
                                .build()
                )
                .build();
    }
}