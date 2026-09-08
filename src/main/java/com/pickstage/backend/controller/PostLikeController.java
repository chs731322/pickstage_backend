package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.PostLikeService;

@RestController
@RequestMapping("/api/posts")
public class PostLikeController {
    private final PostLikeService postLikeService;

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    // 게시글 좋아요 추가
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<String>> addLike(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
        postLikeService.addLike(userId, postId);
        return ResponseEntity.status(201).body(ApiResponse.success("게시글을 좋아요했습니다."));
    }

    // 게시글 좋아요 취소
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<String>> removeLike(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
        postLikeService.removeLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success("좋아요를 취소했습니다."));
    }
}
