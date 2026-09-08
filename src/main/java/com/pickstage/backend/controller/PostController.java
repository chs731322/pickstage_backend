package com.pickstage.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.PostCreateRequest;
import com.pickstage.backend.dto.PostDetailResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.dto.PostUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.PostService;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // 게시글 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long festivalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PostListResponse> result = postService.getPosts(category, festivalId, pageable);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
    }

    // 게시글 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        PostDetailResponse result = postService.getPost(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 게시글 작성
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createPost(
            @AuthenticationPrincipal Long userId, @RequestBody PostCreateRequest request) {

        postService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게시글이 작성되었습니다."));
    }

    // 게시글 수정
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updatePost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody PostUpdateRequest request) {

        postService.updatePost(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("게시글이 수정되었습니다."));
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {

        postService.deletePost(id, userId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다."));
    }
}
