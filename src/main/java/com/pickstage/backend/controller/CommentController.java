package com.pickstage.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.pickstage.backend.dto.CommentCreateRequest;
import com.pickstage.backend.dto.CommentUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.CommentService;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // 댓글 작성
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Void>> createComment(
            @AuthenticationPrincipal Long userId, @PathVariable Long postId,
            @RequestBody CommentCreateRequest request) {

        commentService.createComment(postId, userId, request.getContent(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("댓글이 작성되었습니다."));
    }

    // 댓글 수정
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> updateComment(
            @AuthenticationPrincipal Long userId, @PathVariable Long commentId,
            @RequestBody CommentUpdateRequest request) {

        commentService.updateComment(commentId, userId, request.getContent());
        return ResponseEntity.ok(ApiResponse.success("댓글이 수정되었습니다."));
    }

    // 댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Long userId, @PathVariable Long commentId) {

        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다."));
    }
}
