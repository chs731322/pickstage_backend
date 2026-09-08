package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.PostLike;
import com.pickstage.backend.repository.PostLikeRepository;
import com.pickstage.backend.repository.PostRepository;

@Service
public class PostLikeService {
    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    public PostLikeService(PostLikeRepository postLikeRepository, PostRepository postRepository,
            PostService postService) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
        this.postService = postService;
    }

    // 게시글 좋아요 추가
    @Transactional
    public void addLike(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."));

        if (postLikeRepository.existsByPostIdAndUserId(post.getId(), userId)) {
            throw new DataIntegrityViolationException("이미 좋아요한 게시글입니다.");
        }

        postLikeRepository.save(
                PostLike.builder().postId(post.getId()).userId(userId).createdAt(LocalDateTime.now()).build());

        postService.incrementLikeCount(post.getId());
    }

    // 게시글 좋아요 취소
    @Transactional
    public void removeLike(Long userId, Long postId) {
        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new DataIntegrityViolationException("좋아요하지 않은 게시글입니다.");
        }

        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
        postService.decrementLikeCount(postId);
    }
}
