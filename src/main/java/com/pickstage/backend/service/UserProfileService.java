package com.pickstage.backend.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.FestivalListResponse;
import com.pickstage.backend.dto.InterestArtistResponse;
import com.pickstage.backend.dto.MyReviewResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.dto.PrivacySettings;
import com.pickstage.backend.dto.PrivacyUpdateRequest;
import com.pickstage.backend.dto.UserProfileResponse;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.UserPrivacy;
import com.pickstage.backend.repository.UserPrivacyRepository;
import com.pickstage.backend.repository.UserRepository;

// 로그인한 사용자가 다른 사용자의 프로필(찜한 공연, 관심 아티스트, 쓴 글, 공연장 후기)을 보는 기능.
// 항목마다 본인이 비공개로 바꿀 수 있고, 비공개 항목은 본인만 볼 수 있다.
@Service
public class UserProfileService {

    private static final String PRIVATE_MESSAGE = "비공개로 설정된 항목이에요.";

    private final UserRepository userRepository;
    private final UserPrivacyRepository userPrivacyRepository;
    private final BookmarkService bookmarkService;
    private final InterestArtistService interestArtistService;
    private final PostService postService;
    private final ReviewService reviewService;

    public UserProfileService(
            UserRepository userRepository,
            UserPrivacyRepository userPrivacyRepository,
            BookmarkService bookmarkService,
            InterestArtistService interestArtistService,
            PostService postService,
            ReviewService reviewService) {
        this.userRepository = userRepository;
        this.userPrivacyRepository = userPrivacyRepository;
        this.bookmarkService = bookmarkService;
        this.interestArtistService = interestArtistService;
        this.postService = postService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long viewerId, Long targetId) {
        User user = findActiveUser(targetId);
        boolean self = viewerId.equals(targetId);
        return UserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage())
                .createdAt(user.getCreatedAt())
                .self(self)
                .visible(self ? PrivacySettings.allVisible() : PrivacySettings.of(privacyOf(targetId)))
                .build();
    }

    @Transactional(readOnly = true)
    public List<FestivalListResponse> getBookmarks(Long viewerId, Long targetId) {
        requireVisible(viewerId, targetId, UserPrivacy::isShowBookmarks);
        return bookmarkService.getUserBookmarks(targetId);
    }

    @Transactional(readOnly = true)
    public List<InterestArtistResponse> getArtists(Long viewerId, Long targetId) {
        requireVisible(viewerId, targetId, UserPrivacy::isShowArtists);
        return interestArtistService.getMyInterestArtists(targetId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getPosts(Long viewerId, Long targetId, int page, int size) {
        requireVisible(viewerId, targetId, UserPrivacy::isShowPosts);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.of(postService.getMyPosts(targetId, null, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<MyReviewResponse> getReviews(Long viewerId, Long targetId, int page, int size) {
        requireVisible(viewerId, targetId, UserPrivacy::isShowReviews);
        return reviewService.getMyReviews(targetId, page, size);
    }

    @Transactional(readOnly = true)
    public PrivacySettings getPrivacy(Long userId) {
        return PrivacySettings.of(privacyOf(userId));
    }

    @Transactional
    public PrivacySettings updatePrivacy(Long userId, PrivacyUpdateRequest request) {
        UserPrivacy privacy = userPrivacyRepository.findById(userId).orElseGet(() -> UserPrivacy.defaultsFor(userId));
        privacy.update(request.getShowBookmarks(), request.getShowArtists(), request.getShowPosts(), request.getShowReviews());
        return PrivacySettings.of(userPrivacyRepository.save(privacy));
    }

    private UserPrivacy privacyOf(Long userId) {
        return userPrivacyRepository.findById(userId).orElseGet(() -> UserPrivacy.defaultsFor(userId));
    }

    // 탈퇴했거나 없는 회원이면 404 (IllegalStateException이 404로 처리된다)
    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회원입니다."));
    }

    private void requireVisible(Long viewerId, Long targetId, java.util.function.Predicate<UserPrivacy> visible) {
        findActiveUser(targetId);
        if (viewerId.equals(targetId)) {
            return;
        }
        if (!visible.test(privacyOf(targetId))) {
            throw new AccessDeniedException(PRIVATE_MESSAGE);
        }
    }
}
