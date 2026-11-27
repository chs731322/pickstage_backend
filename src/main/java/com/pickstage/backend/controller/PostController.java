package com.pickstage.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.PostCreateRequest;
import com.pickstage.backend.dto.PostDetailResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.dto.PostUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.global.util.ViewerKeys;
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
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long festivalId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort) {

        Pageable pageable = PageRequest.of(page, size, postSort(sort));
        Page<PostListResponse> result = postService.getPosts(category, festivalId, keyword, pageable, userId);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
    }

    // 내가 쓴 글 목록 (마이페이지). 최신순, 카테고리로 거를 수 있다. 로그인 필요.
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> getMyPosts(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, postSort("latest"));
        Page<PostListResponse> result = postService.getMyPosts(userId, category, pageable);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
    }

    // 목록 정렬: latest(최신순, 기본) / likes(좋아요순) / views(조회순). 같은 값이면 최신 글이 먼저 나온다.
    private Sort postSort(String sort) {
        Sort byNewest = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        if ("likes".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "likeCount").and(byNewest);
        }
        if ("views".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "viewCount").and(byNewest);
        }
        return byNewest;
    }

    // 게시글 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, HttpServletRequest request) {
        PostDetailResponse result = postService.getPost(id, userId, ViewerKeys.of(userId, request));
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
