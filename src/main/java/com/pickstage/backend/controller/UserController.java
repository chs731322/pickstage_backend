package com.pickstage.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;

import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    // 닉네임 중복 확인
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkNickname(
            @RequestParam
            @NotBlank(message = "닉네임을 입력해주세요.")
            String nickname) {

        boolean available = authService.isNicknameAvailable(nickname);

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
}