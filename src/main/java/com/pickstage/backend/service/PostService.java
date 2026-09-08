package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.PostCreateRequest;
import com.pickstage.backend.dto.PostDetailResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.dto.PostUpdateRequest;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.PostImage;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.dto.CommentResponse;
import com.pickstage.backend.repository.PostImageRepository;
import com.pickstage.backend.repository.PostLikeRepository;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final FestivalRepository festivalRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;

    public PostService(PostRepository postRepository, FestivalRepository festivalRepository,
            UserRepository userRepository, CommentRepository commentRepository,
            PostImageRepository postImageRepository, PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.festivalRepository = festivalRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postImageRepository = postImageRepository;
        this.postLikeRepository = postLikeRepository;
    }

    // 게시글 목록 조회
    public Page<PostListResponse> getPosts(String category, Long festivalId, Pageable pageable) {
        Post.Category categoryEnum = parseCategory(category);

        return postRepository.findByFilters(categoryEnum, festivalId, pageable)
                .map(post -> PostListResponse.builder()
                        .id(post.getId())
                        .category(post.getCategory().name())
                        .title(post.getTitle())
                        .authorNickname(getNickname(post.getUserId()))
                        .festivalId(post.getFestivalId())
                        .festivalTitle(getFestivalTitle(post.getFestivalId()))
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .createdAt(post.getCreatedAt())
                        .build());
    }

    // 게시글 상세 조회
    @Transactional
    public PostDetailResponse getPost(Long postId, Long userId) {
        Post post = findVisiblePost(postId);

        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        return PostDetailResponse.builder()
                .id(post.getId())
                .category(post.getCategory().name())
                .title(post.getTitle())
                .content(post.getContent())
                .authorNickname(getNickname(post.getUserId()))
                .festivalId(post.getFestivalId())
                .festivalTitle(getFestivalTitle(post.getFestivalId()))
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .liked(userId != null && postLikeRepository.existsByPostIdAndUserId(postId, userId))
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .comments(commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId).stream()
                        .map(comment -> CommentResponse.builder()
                                .id(comment.getId())
                                .parentId(comment.getParentId())
                                .content(comment.getContent())
                                .authorNickname(getNickname(comment.getUserId()))
                                .createdAt(comment.getCreatedAt())
                                .build())
                        .toList())
                .images(postImageRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                        .map(PostImage::getImageUrl)
                        .toList())
                .build();
    }

    // 게시글 작성
    @Transactional
    public void createPost(Long userId, PostCreateRequest request) {
        if (request.getFestivalId() != null && !festivalRepository.existsById(request.getFestivalId())) {
            throw new IllegalStateException("존재하지 않는 공연입니다.");
        }

        Post post = Post.builder()
                .userId(userId)
                .festivalId(request.getFestivalId())
                .category(parseCategory(request.getCategory()))
                .title(request.getTitle())
                .content(request.getContent())
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        postRepository.save(post);

        if (request.getImages() != null) {
            for (int i = 0; i < request.getImages().size(); i++) {
                String url = request.getImages().get(i);
                PostImage postImage = new PostImage(null, post.getId(), url, i);
                postImageRepository.save(postImage);
            }
        }
    }

    // 게시글 수정
    @Transactional
    public void updatePost(Long postId, Long userId, PostUpdateRequest request) {
        Post post = findOwnedPost(postId, userId, "수정 권한이 없습니다.");

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        postRepository.save(post);

        if (request.getImages() != null) {
            postImageRepository.deleteByPostId(postId);
            for (int i = 0; i < request.getImages().size(); i++) {
                String url = request.getImages().get(i);
                PostImage postImage = new PostImage(null, postId, url, i);
                postImageRepository.save(postImage);
            }
        }
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findOwnedPost(postId, userId, "삭제 권한이 없습니다.");
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    // 소프트 삭제되지 않은 게시글 조회
    private Post findVisiblePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."));

        if (post.getDeletedAt() != null) {
            throw new IllegalStateException("존재하지 않는 게시글입니다.");
        }
        return post;
    }

    // 게시글 조회 + 본인 소유 확인 공통 로직
    private Post findOwnedPost(Long postId, Long userId, String forbiddenMessage) {
        Post post = findVisiblePost(postId);

        if (!post.getUserId().equals(userId)) {
            throw new AccessDeniedException(forbiddenMessage);
        }
        return post;
    }

    @Transactional
    public void forceDeletePost(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }

        Post post = findVisiblePost(postId);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    // 좋아요 수 증가
    @Transactional
    public void incrementLikeCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."));
        post.incrementLikeCount();
        postRepository.save(post);
    }

    // 좋아요 수 감소
    @Transactional
    public void decrementLikeCount(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."));
        post.decrementLikeCount();
        postRepository.save(post);
    }

    private Post.Category parseCategory(String category) {
        if (category == null) {
            return null;
        }
        try {
            return Post.Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 요청입니다.");
        }
    }

    private String getNickname(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getNickname())
                .orElse(null);
    }

    private String getFestivalTitle(Long festivalId) {
        if (festivalId == null) {
            return null;
        }
        return festivalRepository.findById(festivalId)
                .map(festival -> festival.getTitle())
                .orElse(null);
    }
}
