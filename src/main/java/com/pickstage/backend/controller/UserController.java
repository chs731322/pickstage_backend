package com.pickstage.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.FestivalListResponse;
import com.pickstage.backend.dto.InterestArtistResponse;
import com.pickstage.backend.dto.MyPageResponse;
import com.pickstage.backend.dto.MyReviewResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.dto.PrivacySettings;
import com.pickstage.backend.dto.PrivacyUpdateRequest;
import com.pickstage.backend.dto.UserProfileResponse;
import com.pickstage.backend.dto.MyPageUpdateRequest;
import com.pickstage.backend.dto.PasswordChangeRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.service.UserProfileService;
import com.pickstage.backend.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final UserService userService;
    private final UserProfileService userProfileService;

    public UserController(
            AuthService authService,
            UserService userService,
            UserProfileService userProfileService) {

        this.authService = authService;
        this.userService = userService;
        this.userProfileService = userProfileService;
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

    // 내 프로필 공개 설정 조회
    @GetMapping("/mypage/privacy")
    public ResponseEntity<ApiResponse<PrivacySettings>> getPrivacy(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getPrivacy(userId)));
    }

    // 내 프로필 공개 설정 변경 (값을 보낸 항목만 바뀐다)
    @PatchMapping("/mypage/privacy")
    public ResponseEntity<ApiResponse<PrivacySettings>> updatePrivacy(
            @AuthenticationPrincipal Long userId,
            @RequestBody PrivacyUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.updatePrivacy(userId, request)));
    }

    // 다른 사용자 프로필 (로그인 필요). 비공개 항목은 본인만 볼 수 있다.
    @GetMapping("/{targetId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetId) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getProfile(userId, targetId)));
    }

    @GetMapping("/{targetId}/bookmarks")
    public ResponseEntity<ApiResponse<List<FestivalListResponse>>> getUserBookmarks(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetId) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getBookmarks(userId, targetId)));
    }

    @GetMapping("/{targetId}/artists")
    public ResponseEntity<ApiResponse<List<InterestArtistResponse>>> getUserArtists(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetId) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getArtists(userId, targetId)));
    }

    @GetMapping("/{targetId}/posts")
    public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> getUserPosts(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getPosts(userId, targetId, page, size)));
    }

    @GetMapping("/{targetId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<MyReviewResponse>>> getUserReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(userProfileService.getReviews(userId, targetId, page, size)));
    }
}
