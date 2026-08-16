package com.pickstage.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.MyPageResponse;
import com.pickstage.backend.dto.MyPageUpdateRequest;
import com.pickstage.backend.dto.PasswordChangeRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(
            AuthService authService,
            UserService userService) {

        this.authService = authService;
        this.userService = userService;
    }

    // 닉네임 중복 확인
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkNickname(
            @RequestParam
            @NotBlank(message = "닉네임을 입력해주세요.")
            String nickname) {

        boolean available =
                authService.isNicknameAvailable(nickname);

        Map<String, Boolean> data = Map.of(
                "available", available
        );

        String message = available
                ? "사용 가능한 닉네임입니다."
                : "이미 사용 중인 닉네임입니다.";

        return ResponseEntity.ok(
                ApiResponse.success(data, message)
        );
    }

    // 내 정보 조회
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(
            @AuthenticationPrincipal Long userId) {

        MyPageResponse response =
                userService.getMyPage(userId);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 내 정보 수정
    @PatchMapping("/mypage")
    public ResponseEntity<ApiResponse<MyPageResponse>> updateMyPage(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MyPageUpdateRequest request) {

        MyPageResponse response =
                userService.updateMyPage(
                        userId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    // 비밀번호 변경
    @PatchMapping("/mypage/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request) {

        userService.changePassword(
                userId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "비밀번호가 변경되었습니다."
                )
        );
    }

    // 회원 탈퇴
    @DeleteMapping("/mypage")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId) {

        userService.withdraw(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "회원 탈퇴가 완료되었습니다."
                )
        );
    }
}