package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import com.pickstage.backend.entity.Comment;
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
    private static final int PREVIEW_MAX_LENGTH = 120;
    // 사진만 있는 한 줄: ![설명](주소)
    private static final Pattern MARKDOWN_IMAGE_LINE = Pattern.compile("^!\\[[^\\]]*\\]\\([^)\\s]*\\)$");
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
    private final BlockService blockService;
    private final ProfanityFilter profanityFilter;

    public PostService(PostRepository postRepository, FestivalRepository festivalRepository,
            UserRepository userRepository, CommentRepository commentRepository,
            PostImageRepository postImageRepository, PostLikeRepository postLikeRepository,
            PostViewGuard postViewGuard, BlockService blockService, ProfanityFilter profanityFilter) {
        this.blockService = blockService;
        this.profanityFilter = profanityFilter;
        this.postRepository = postRepository;
        this.festivalRepository = festivalRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postImageRepository = postImageRepository;
        this.postViewGuard = postViewGuard;
        this.postLikeRepository = postLikeRepository;
    }

    // 게시글 목록 조회 (로그인했다면, 내가 차단한 사용자의 글은 빼고 보여준다)
    // keyword: 제목·본문·연결된 공연명에 포함된 글자 (비어 있으면 검색하지 않는다)
    public Page<PostListResponse> getPosts(String category, Long festivalId, String keyword, Pageable pageable, Long viewerId) {
        Post.Category categoryEnum = parseCategory(category);
        String trimmedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();

        // NOT IN 에 빈 목록을 넣으면 쿼리가 깨질 수 있어서, 차단한 사람이 없으면 존재하지 않는 id 하나를 넣는다
        Set<Long> blocked = blockService.getBlockedIds(viewerId);
        Collection<Long> excluded = blocked.isEmpty() ? List.of(-1L) : blocked;

        Map<Long, User> authors = new HashMap<>();
        return postRepository.findByFilters(categoryEnum, festivalId, trimmedKeyword, excluded, pageable)
                .map(post -> toListResponse(post, authors));
    }

    // 내가 쓴 글 목록 (마이페이지)
    public Page<PostListResponse> getMyPosts(Long userId, String category, Pageable pageable) {
        Post.Category categoryEnum = parseCategory(category);

        Map<Long, User> authors = new HashMap<>();
        return postRepository.findMine(userId, categoryEnum, pageable)
                .map(post -> toListResponse(post, authors));
    }

    private PostListResponse toListResponse(Post post, Map<Long, User> authors) {
        return PostListResponse.builder()
                .id(post.getId())
                .category(post.getCategory().name())
                .title(post.getTitle())
                .authorId(authorIdOf(authors, post.getUserId()))
                .authorNickname(nicknameOf(authors, post.getUserId()))
                .authorProfileImage(profileImageOf(authors, post.getUserId()))
                .festivalId(post.getFestivalId())
                .festivalTitle(getFestivalTitle(post.getFestivalId()))
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount((int) commentRepository.countByPostIdAndDeletedAtIsNullAndHiddenAtIsNull(post.getId()))
                .preview(previewOf(post.getContent()))
                .createdAt(post.getCreatedAt())
                .build();
    }

    // 본문에서 사진 줄을 빼고 줄바꿈을 공백으로 이어 앞부분만 잘라 미리보기 글로 만든다
    private String previewOf(String content) {
        if (content == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String line : content.split("\n")) {
            if (!MARKDOWN_IMAGE_LINE.matcher(line.trim()).matches()) {
                text.append(line).append(' ');
            }
        }
        String collapsed = text.toString().replaceAll("\\s+", " ").trim();
        return collapsed.length() > PREVIEW_MAX_LENGTH ? collapsed.substring(0, PREVIEW_MAX_LENGTH) + "…" : collapsed;
    }

    // 게시글 상세 조회
    @Transactional
    public PostDetailResponse getPost(Long postId, Long userId, String viewerKey) {
        Post post = findVisiblePost(postId);
        if (post.getHiddenAt() != null) {
            throw new IllegalStateException("신고가 접수되어 검토 중인 게시글입니다.");
        }
        Set<Long> blocked = blockService.getBlockedIds(userId);
        if (blocked.contains(post.getUserId())) {
            throw new IllegalStateException("차단한 사용자의 게시글입니다.");
        }

        // 글쓴이 본인의 조회와 같은 조회자의 반복 조회(새로고침, 좋아요·댓글 뒤 재조회)는 조회수에 더하지 않는다
        boolean isAuthor = userId != null && userId.equals(post.getUserId());
        if (!isAuthor && postViewGuard.shouldCount(postId, viewerKey)) {
            post.setViewCount(post.getViewCount() + 1);
            postRepository.save(post);
        }

        Map<Long, User> authors = new HashMap<>();
        return PostDetailResponse.builder()
                .id(post.getId())
                .category(post.getCategory().name())
                .title(post.getTitle())
                .content(post.getContent())
                .authorId(authorIdOf(authors, post.getUserId()))
                .authorNickname(nicknameOf(authors, post.getUserId()))
                .authorProfileImage(profileImageOf(authors, post.getUserId()))
                .festivalId(post.getFestivalId())
                .festivalTitle(getFestivalTitle(post.getFestivalId()))
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .liked(userId != null && postLikeRepository.existsByPostIdAndUserId(postId, userId))
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .comments(visibleComments(postId, blocked).stream()
                        .map(comment -> CommentResponse.builder()
                                .id(comment.getId())
                                .parentId(comment.getParentId())
                                .content(comment.getContent())
                                .authorId(authorIdOf(authors, comment.getUserId()))
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

    // 내가 차단한 사용자의 댓글, 그리고 그 댓글에 달린 답글은 빼고 돌려준다
    private List<Comment> visibleComments(Long postId, Set<Long> blocked) {
        List<Comment> all = commentRepository.findByPostIdAndDeletedAtIsNullAndHiddenAtIsNullOrderByCreatedAtAsc(postId);
        if (blocked.isEmpty()) {
            return all;
        }
        Set<Long> removedIds = all.stream()
                .filter(c -> blocked.contains(c.getUserId()))
                .map(Comment::getId)
                .collect(Collectors.toSet());
        return all.stream()
                .filter(c -> !removedIds.contains(c.getId()))
                .filter(c -> c.getParentId() == null || !removedIds.contains(c.getParentId()))
                .toList();
    }

    // 게시글 작성
    @Transactional
    public void createPost(Long userId, PostCreateRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("제목과 내용을 입력해주세요.");
        }
        validatePostBody(request.getTitle(), request.getContent(), request.getImages());
        profanityFilter.assertClean(request.getTitle(), request.getContent());
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
        profanityFilter.assertClean(request.getTitle(), request.getContent());

        // 카테고리는 보낸 경우에만 바꾼다 (허용되지 않는 값이면 parseCategory가 거절한다)
        if (request.getCategory() != null) {
            post.setCategory(parseCategory(request.getCategory()));
        }
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

    private Long authorIdOf(Map<Long, User> authors, Long userId) {
        User author = authorOf(authors, userId);
        return author == null || author.getDeletedAt() != null ? null : author.getId();
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
