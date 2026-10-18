package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;

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
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int CONTENT_MAX_LENGTH = 20000;
    private static final int IMAGES_MAX_COUNT = 30;
    // 본문 안의 사진 표기: ![설명](주소)
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]*)\\)");

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    private final PostRepository postRepository;
    private final FestivalRepository festivalRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostViewGuard postViewGuard;

    public PostService(PostRepository postRepository, FestivalRepository festivalRepository,
            UserRepository userRepository, CommentRepository commentRepository,
            PostImageRepository postImageRepository, PostLikeRepository postLikeRepository,
            PostViewGuard postViewGuard) {
        this.postRepository = postRepository;
        this.festivalRepository = festivalRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postImageRepository = postImageRepository;
        this.postViewGuard = postViewGuard;
        this.postLikeRepository = postLikeRepository;
    }

    // 게시글 목록 조회
    public Page<PostListResponse> getPosts(String category, Long festivalId, Pageable pageable) {
        Post.Category categoryEnum = parseCategory(category);

        Map<Long, User> authors = new HashMap<>();
        return postRepository.findByFilters(categoryEnum, festivalId, pageable)
                .map(post -> PostListResponse.builder()
                        .id(post.getId())
                        .category(post.getCategory().name())
                        .title(post.getTitle())
                        .authorNickname(nicknameOf(authors, post.getUserId()))
                        .authorProfileImage(profileImageOf(authors, post.getUserId()))
                        .festivalId(post.getFestivalId())
                        .festivalTitle(getFestivalTitle(post.getFestivalId()))
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .createdAt(post.getCreatedAt())
                        .build());
    }

    // 게시글 상세 조회
    @Transactional
    public PostDetailResponse getPost(Long postId, Long userId, String viewerKey) {
        Post post = findVisiblePost(postId);

        // 같은 조회자의 반복 조회(새로고침, 좋아요·댓글 뒤 재조회)는 조회수에 더하지 않는다
        if (postViewGuard.shouldCount(postId, viewerKey)) {
            post.setViewCount(post.getViewCount() + 1);
            postRepository.save(post);
        }

        Map<Long, User> authors = new HashMap<>();
        return PostDetailResponse.builder()
                .id(post.getId())
                .category(post.getCategory().name())
                .title(post.getTitle())
                .content(post.getContent())
                .authorNickname(nicknameOf(authors, post.getUserId()))
                .authorProfileImage(profileImageOf(authors, post.getUserId()))
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
                                .authorNickname(nicknameOf(authors, comment.getUserId()))
                                .authorProfileImage(profileImageOf(authors, comment.getUserId()))
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
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("제목과 내용을 입력해주세요.");
        }
        validatePostBody(request.getTitle(), request.getContent(), request.getImages());
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
        validatePostBody(request.getTitle(), request.getContent(), request.getImages());

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

    // 제목·본문 길이와 사진 주소를 검사한다. 사진은 우리 이미지 저장소 주소만 허용한다. (null인 값은 건너뜀)
    private void validatePostBody(String title, String content, List<String> images) {
        String allowedPrefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        if (title != null && (title.isBlank() || title.length() > TITLE_MAX_LENGTH)) {
            throw new IllegalArgumentException("제목은 1~" + TITLE_MAX_LENGTH + "자로 입력해주세요.");
        }
        if (content != null) {
            if (content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
                throw new IllegalArgumentException("내용은 1~" + CONTENT_MAX_LENGTH + "자로 입력해주세요.");
            }
            Matcher matcher = MARKDOWN_IMAGE.matcher(content);
            while (matcher.find()) {
                if (!matcher.group(1).startsWith(allowedPrefix)) {
                    throw new IllegalArgumentException("본문의 사진은 업로드한 이미지만 사용할 수 있습니다.");
                }
            }
        }
        if (images != null) {
            if (images.size() > IMAGES_MAX_COUNT) {
                throw new IllegalArgumentException("사진은 최대 " + IMAGES_MAX_COUNT + "장까지 올릴 수 있습니다.");
            }
            for (String url : images) {
                if (url == null || !url.startsWith(allowedPrefix)) {
                    throw new IllegalArgumentException("업로드한 이미지만 사용할 수 있습니다.");
                }
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

    // 같은 요청 안에서 같은 작성자는 한 번만 조회한다 (게시글·댓글 작성자 중복 조회 방지)
    private User authorOf(Map<Long, User> authors, Long userId) {
        if (userId == null) {
            return null;
        }
        return authors.computeIfAbsent(userId, id -> userRepository.findById(id).orElse(null));
    }

    private String nicknameOf(Map<Long, User> authors, Long userId) {
        User author = authorOf(authors, userId);
        return author == null ? null : author.getNickname();
    }

    private String profileImageOf(Map<Long, User> authors, Long userId) {
        User author = authorOf(authors, userId);
        return author == null ? null : author.getProfileImage();
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
