package com.pickstage.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.dto.ReissueRequest;
import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.dto.SignupRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.dto.ReissueRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 로그아웃
    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signout(
            @AuthenticationPrincipal Long userId) {

        authService.signout(userId);
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다."));
    }
    
 // 토큰 재발급
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<AuthResponse>> reissue(
            @RequestBody ReissueRequest request) {

        AuthResponse response = authService.reissue(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}