package com.pickstage.backend.service;

import com.pickstage.backend.dto.AdminUserResponse;
import com.pickstage.backend.dto.MyPageResponse;
import com.pickstage.backend.dto.MyPageUpdateRequest;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.PasswordChangeRequest;
import com.pickstage.backend.dto.RoleUpdateRequest;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.ChecklistRepository;
import com.pickstage.backend.repository.EmailVerificationRepository;
import com.pickstage.backend.repository.InterestArtistRepository;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PasswordResetTokenRepository;
import com.pickstage.backend.repository.PushTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserBlockRepository;
import com.pickstage.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

        private final UserRepository userRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final PasswordResetTokenRepository passwordResetTokenRepository;
        private final EmailVerificationRepository emailVerificationRepository;
        private final InterestArtistRepository interestArtistRepository;
        private final ChecklistRepository checklistRepository;
        private final NotificationRepository notificationRepository;
        private final BookmarkService bookmarkService;
        private final UploadedImagePolicy uploadedImagePolicy;
        private final ProfanityFilter profanityFilter;
        private final UserBlockRepository userBlockRepository;
        private final PushTokenRepository pushTokenRepository;

        public UserService(
                        UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        EmailVerificationRepository emailVerificationRepository,
                        InterestArtistRepository interestArtistRepository,
                        ChecklistRepository checklistRepository,
                        NotificationRepository notificationRepository,
                        BookmarkService bookmarkService,
                        UploadedImagePolicy uploadedImagePolicy,
                        ProfanityFilter profanityFilter,
                        UserBlockRepository userBlockRepository,
                        PushTokenRepository pushTokenRepository) {

                this.pushTokenRepository = pushTokenRepository;
                this.userBlockRepository = userBlockRepository;
                this.profanityFilter = profanityFilter;
                this.userRepository = userRepository;
                this.refreshTokenRepository = refreshTokenRepository;
                this.passwordEncoder = passwordEncoder;
                this.passwordResetTokenRepository = passwordResetTokenRepository;
                this.emailVerificationRepository = emailVerificationRepository;
                this.interestArtistRepository = interestArtistRepository;
                this.checklistRepository = checklistRepository;
                this.notificationRepository = notificationRepository;
                this.bookmarkService = bookmarkService;
                this.uploadedImagePolicy = uploadedImagePolicy;
        }

        // 내 정보 조회
        @Transactional(readOnly = true)
        public MyPageResponse getMyPage(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "사용자를 찾을 수 없습니다."));

                return MyPageResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .nickname(user.getNickname())
                                .profileImage(user.getProfileImage())
                                .role(user.getRole().name())
                                .createdAt(user.getCreatedAt())
                                .build();
        }

        // 내 정보 수정
        @Transactional
        public MyPageResponse updateMyPage(
                        Long userId,
                        MyPageUpdateRequest request) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "사용자를 찾을 수 없습니다."));

                profanityFilter.assertClean(request.getNickname());

                // 닉네임이 전달된 경우에만 중복 확인 하게끔
                if (request.getNickname() != null
                                && !request.getNickname()
                                                .equals(user.getNickname())
                                && userRepository.existsByNickname(
                                                request.getNickname())) {

                        throw new IllegalArgumentException(
                                        "이미 사용 중인 닉네임입니다.");
                }

                // 프로필 이미지는 우리 저장소에 올린 사진만 쓸 수 있다 (빈 값은 기본 이미지로 되돌리기)
                String profileImage = request.getProfileImage();
                if (profileImage != null && !profileImage.isEmpty()
                                && !uploadedImagePolicy.isUploadedImageUrl(profileImage)) {
                        throw new IllegalArgumentException("프로필 사진은 업로드한 이미지만 사용할 수 있습니다.");
                }

                // 닉네임 및 프로필 이미지 수정
                user.updateProfile(
                                request.getNickname(),
                                request.getProfileImage());

                return MyPageResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .nickname(user.getNickname())
                                .profileImage(user.getProfileImage())
                                .role(user.getRole().name())
                                .createdAt(user.getCreatedAt())
                                .build();
        }

        // 비밀번호 변경
        @Transactional
        public void changePassword(
                        Long userId,
                        PasswordChangeRequest request) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "사용자를 찾을 수 없습니다."));

                // 소셜 로그인 사용자는 비밀번호 변경 불가
                if (user.getSocialProvider() != User.SocialProvider.none) {

                        throw new IllegalArgumentException(
                                        "소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다.");
                }

                // 현재 비밀번호 확인
                if (!passwordEncoder.matches(
                                request.getCurrentPassword(),
                                user.getPasswordHash())) {

                        throw new IllegalArgumentException(
                                        "현재 비밀번호가 일치하지 않습니다.");
                }

                // 새 비밀번호 암호화
                String encodedPassword = passwordEncoder.encode(
                                request.getNewPassword());

                // 비밀번호 변경
                user.updatePassword(encodedPassword);

                // 다른 기기에 남아 있는 로그인(리프레시 토큰)을 끊는다. 지금 쓰는 액세스 토큰은 만료(30분)까지 유효하다.
                refreshTokenRepository.deleteByUserId(userId);
        }

        // 회원 탈퇴
        @Transactional
        public void withdraw(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "사용자를 찾을 수 없습니다."));

                String oldEmail = user.getEmail();

                // 개인 식별 정보를 지우고 계정 비활성화 (게시글·댓글·리뷰는 "탈퇴한 회원" 이름으로 남는다)
                user.withdraw();

                // 인증·재설정 관련 정보 삭제
                refreshTokenRepository.deleteByUserId(userId);
                passwordResetTokenRepository.deleteByUserId(userId);
                emailVerificationRepository.deleteByEmail(oldEmail);

                // 개인 데이터 삭제: 찜(공연 찜 수도 함께 감소), 관심 아티스트, 체크리스트, 알림
                bookmarkService.removeAllBookmarks(userId);
                interestArtistRepository.deleteByUserId(userId);
                checklistRepository.deleteByUserId(userId);
                notificationRepository.deleteByUserId(userId);
                // 차단 기록(내가 차단한 사람, 나를 차단한 사람)도 지운다
                userBlockRepository.deleteByBlockerIdOrBlockedId(userId, userId);
                // 푸시 알림을 받던 기기 정보도 지운다
                pushTokenRepository.deleteByUserId(userId);
        }

        // 회원 목록 조회
        // sort: latest(최근 가입순, 기본) / oldest(오래된 가입순)
        @Transactional(readOnly = true)
        public PageResponse<AdminUserResponse> getUsers(
                        Long requesterId, String keyword, String role, boolean withdrawnOnly, boolean excludeDummy,
                        String sort, int page, int size) {
                User requester = userRepository.findById(requesterId)
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

                if (!requester.getRole().equals(User.Role.admin)) {
                        throw new org.springframework.security.access.AccessDeniedException("관리자 권한이 없습니다.");
                }

                Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), userListSort(sort));
                Page<User> users = userRepository.findByFilters(keyword, parseRole(role), withdrawnOnly, excludeDummy, pageable);

                Page<AdminUserResponse> content = users.map(user -> AdminUserResponse.builder()
                                .id(user.getId())
                                .email(user.getEmail())
                                .nickname(user.getNickname())
                                .profileImage(user.getProfileImage())
                                .role(user.getRole().name())
                                .isBanned(user.getBannedAt() != null)
                                .isWithdrawn(user.getDeletedAt() != null)
                                .createdAt(user.getCreatedAt())
                                .build());

                return PageResponse.of(content);
        }

        static Sort userListSort(String sort) {
                if ("oldest".equals(sort)) {
                        return Sort.by(Sort.Direction.ASC, "createdAt");
                }
                return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        private User.Role parseRole(String role) {
                if (role == null) {
                        return null;
                }
                try {
                        return User.Role.valueOf(role);
                } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("올바르지 않은 요청입니다.");
                }
        }

        // 회원 권한 변경
        @Transactional
        public void updateRole(
                        Long requesterId,
                        Long targetUserId,
                        RoleUpdateRequest roleUpdateRequest) {
                User requester = userRepository.findById(requesterId)
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

                User targetUser = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

                if (!requester.getRole().equals(User.Role.admin)) {
                        throw new org.springframework.security.access.AccessDeniedException("관리자 권한이 없습니다.");
                }

                // 자기 자신 권한 변경 금지
                if (requesterId.equals(targetUserId)) {
                        throw new IllegalArgumentException("올바르지 않은 요청입니다.");
                }

                targetUser.updateRole(parseRole(roleUpdateRequest.getRole()));

        }

}