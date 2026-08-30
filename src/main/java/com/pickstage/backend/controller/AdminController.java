package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.AdminUserResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.RoleUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.PostService;
import com.pickstage.backend.service.ReviewService;
import com.pickstage.backend.service.UserService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PostService postService;
    private final ReviewService reviewService;
    private final UserService userService;

    public AdminController(PostService postService, ReviewService reviewService, UserService userService) {
        this.postService = postService;
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AdminUserResponse> response = userService.getUsers(userId, keyword, role, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> forceDeletePost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.forceDeletePost(userId, id);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다."));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> forceDeleteReview(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        reviewService.forceDeleteReview(userId, id);
        return ResponseEntity.ok(ApiResponse.success("후기가 삭제되었습니다."));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        userService.updateRole(userId, id, request);

        return ResponseEntity.ok(ApiResponse.success("회원 권한이 변경되었습니다."));
    }
}