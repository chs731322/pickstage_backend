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
import com.pickstage.backend.dto.ReissueRequest;
import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.dto.SignupRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @RequestBody SignupRequest request) {

        AuthResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 로그인
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponse>> signin(
            @RequestBody SigninRequest request) {

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
    public ResponseEntity<ApiResponse<AuthResponse>> reissue(
            @RequestBody ReissueRequest request) {

        AuthResponse response =
                authService.reissue(request.getRefreshToken());

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }
}