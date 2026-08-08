package com.pickstage.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.dto.EmailSendRequest;
import com.pickstage.backend.dto.EmailVerifyRequest;
import com.pickstage.backend.dto.ReissueRequest;
import com.pickstage.backend.dto.ReissueResponse;
import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.dto.SignupRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.service.EmailService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    public AuthController(
            AuthService authService,
            EmailService emailService) {

        this.authService = authService;
        this.emailService = emailService;
    }

    // 이메일 중복 확인
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(
            @RequestParam
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.")
            String email) {

        boolean available = authService.isEmailAvailable(email);

        if (!available) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail("이미 가입된 이메일입니다."));
        }

        return ResponseEntity.ok(
                ApiResponse.success("사용 가능한 이메일입니다.")
        );
    }

    // 이메일 인증코드 발송
    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationCode(
            @Valid @RequestBody EmailSendRequest request) {

        emailService.sendVerificationCode(request.getEmail());

        return ResponseEntity.ok(
                ApiResponse.success("인증코드가 발송되었습니다.")
        );
    }

    // 이메일 인증코드 확인
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody EmailVerifyRequest request) {

        emailService.verifyEmail(
                request.getEmail(),
                request.getVerifyCode()
        );

        return ResponseEntity.ok(
                ApiResponse.success("이메일 인증에 성공하였습니다.")
        );
    }

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request) {

        AuthResponse response = authService.signup(request);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 로그인
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponse>> signin(
            @Valid @RequestBody SigninRequest request) {

        AuthResponse response = authService.signin(request);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 로그아웃
    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signout(
            @AuthenticationPrincipal Long userId) {

        authService.signout(userId);

        return ResponseEntity.ok(
                ApiResponse.success("로그아웃되었습니다.")
        );
    }

    // 토큰 재발급
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(
            @Valid @RequestBody ReissueRequest request) {

        ReissueResponse response =
                authService.reissue(request.getRefreshToken());

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }
}