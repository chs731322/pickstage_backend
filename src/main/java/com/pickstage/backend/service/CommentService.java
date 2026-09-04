package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Comment;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.PostRepository;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    // 댓글 작성
    @Transactional
    public void createComment(Long postId, Long userId, String content, Long parentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."));

        if (post.getDeletedAt() != null) {
            throw new IllegalStateException("존재하지 않는 게시글입니다.");
        }

        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 댓글입니다."));
            if (parent.getDeletedAt() != null) {
                throw new IllegalStateException("존재하지 않는 댓글입니다.");
            }
            if (parent.getParentId() != null) {
                throw new IllegalArgumentException("대댓글에는 답글을 달 수 없습니다.");
            }
        }

        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .content(content)
                .parentId(parentId)
                .createdAt(LocalDateTime.now())
                .build();

        commentRepository.save(comment);
    }

    // 댓글 수정
    @Transactional
    public void updateComment(Long commentId, Long userId, String content) {
        Comment comment = findOwnedComment(commentId, userId, "수정 권한이 없습니다.");
        comment.setContent(content);
        commentRepository.save(comment);
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = findOwnedComment(commentId, userId, "삭제 권한이 없습니다.");
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    // 댓글 조회 + 본인 소유 확인 공통 로직
    private Comment findOwnedComment(Long commentId, Long userId, String forbiddenMessage) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 댓글입니다."));

        if (comment.getDeletedAt() != null) {
            throw new IllegalStateException("존재하지 않는 댓글입니다.");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException(forbiddenMessage);
        }
        return comment;
    }
}
