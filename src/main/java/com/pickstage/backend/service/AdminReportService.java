package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.AdminReportResolveRequest;
import com.pickstage.backend.dto.AdminReportResponse;
import com.pickstage.backend.entity.Comment;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.Report;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.PushTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.ReportRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

// 관리자가 들어온 신고를 확인하고 처리한다: 삭제 / 문제 없음(숨김 해제) / 작성자 이용 정지
@Service
public class AdminReportService {

    // 한 번에 보여줄 최대 대상 수 (그 이상은 처리하면서 줄어든다)
    static final int MAX_GROUPS = 100;
    private static final int MAX_DETAILS = 3;
    private static final int SNIPPET_LENGTH = 120;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final VenueReviewRepository venueReviewRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PushTokenRepository pushTokenRepository;

    public AdminReportService(ReportRepository reportRepository, PostRepository postRepository,
            CommentRepository commentRepository, VenueReviewRepository venueReviewRepository,
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.venueReviewRepository = venueReviewRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // 신고 목록 (처리 전 / 처리 완료), 같은 대상에 들어온 신고는 하나로 묶는다
    @Transactional(readOnly = true)
    public List<AdminReportResponse> getReports(Long adminId, boolean done) {
        requireAdmin(adminId);

        List<Report.Status> statuses = done
                ? List.of(Report.Status.DELETED, Report.Status.DISMISSED)
                : List.of(Report.Status.PENDING);

        List<AdminReportResponse> result = new ArrayList<>();
        for (Object[] row : reportRepository.findGroups(statuses, PageRequest.of(0, MAX_GROUPS))) {
            Report.TargetType type = (Report.TargetType) row[0];
            Long targetId = (Long) row[1];
            long count = (Long) row[2];
            LocalDateTime last = (LocalDateTime) row[3];

            List<Report> reports = reportRepository
                    .findByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(type, targetId, statuses);
            result.add(describe(type, targetId, count, last, reports));
        }
        return result;
    }

    // 신고 처리: 삭제 또는 문제 없음(숨김 해제). 같은 대상의 처리 전 신고가 모두 처리 완료로 바뀐다.
    @Transactional
    public void resolve(Long adminId, AdminReportResolveRequest request) {
        requireAdmin(adminId);

        Report.TargetType type = request.getTargetType();
        Long targetId = request.getTargetId();
        boolean delete = request.getAction() == AdminReportResolveRequest.Action.DELETE;

        if (delete && type == Report.TargetType.USER) {
            throw new IllegalArgumentException("사용자는 삭제할 수 없어요. '이용 정지'로 처리해주세요.");
        }

        Report.Status newStatus = delete ? Report.Status.DELETED : Report.Status.DISMISSED;
        int handled = reportRepository.markHandled(type, targetId, Report.Status.PENDING, newStatus,
                LocalDateTime.now(), adminId);
        if (handled == 0) {
            throw new IllegalStateException("처리할 신고가 없습니다.");
        }

        if (delete) {
            softDelete(type, targetId);
        } else {
            unhide(type, targetId);
        }
    }

    // 사용자 이용 정지: 로그인과 API 호출이 막히고, 이 사용자에게 들어온 처리 전 신고는 처리 완료가 된다
    @Transactional
    public void banUser(Long adminId, Long targetUserId) {
        requireAdmin(adminId);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));
        if (target.getDeletedAt() != null) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }
        if (target.getRole() == User.Role.admin || adminId.equals(targetUserId)) {
            throw new IllegalArgumentException("관리자 계정은 정지할 수 없습니다.");
        }
        if (target.getBannedAt() != null) {
            throw new DuplicateRequestException("이미 정지된 사용자입니다.");
        }

        target.ban();
        userRepository.save(target);
        // 갱신 토큰을 지워서, 이미 로그인해 있던 기기도 곧 로그아웃되게 한다
        refreshTokenRepository.deleteByUserId(targetUserId);
        // 정지된 계정에는 푸시도 보내지 않는다 (해제 후 앱을 다시 열면 기기가 다시 등록된다)
        pushTokenRepository.deleteByUserId(targetUserId);
        reportRepository.markHandled(Report.TargetType.USER, targetUserId, Report.Status.PENDING,
                Report.Status.DELETED, LocalDateTime.now(), adminId);
    }

    // 이용 정지 해제
    @Transactional
    public void unbanUser(Long adminId, Long targetUserId) {
        requireAdmin(adminId);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));
        if (target.getBannedAt() == null) {
            throw new IllegalStateException("정지된 사용자가 아닙니다.");
        }
        target.unban();
        userRepository.save(target);
    }

    // ---- 내부 ----

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));
        if (user.getRole() != User.Role.admin) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }

    private void softDelete(Report.TargetType type, Long targetId) {
        LocalDateTime now = LocalDateTime.now();
        switch (type) {
            case POST -> postRepository.findById(targetId).filter(p -> p.getDeletedAt() == null).ifPresent(p -> {
                p.setDeletedAt(now);
                postRepository.save(p);
            });
            case COMMENT -> commentRepository.findById(targetId).filter(c -> c.getDeletedAt() == null).ifPresent(c -> {
                c.setDeletedAt(now);
                commentRepository.save(c);
            });
            case VENUE_REVIEW -> venueReviewRepository.findById(targetId).filter(r -> r.getDeletedAt() == null)
                    .ifPresent(r -> {
                        r.setDeletedAt(now);
                        venueReviewRepository.save(r);
                    });
            default -> {
            }
        }
    }

    // 문제 없음으로 판단하면 임시 숨김을 풀어 다시 보이게 한다
    private void unhide(Report.TargetType type, Long targetId) {
        switch (type) {
            case POST -> postRepository.findById(targetId).filter(p -> p.getHiddenAt() != null).ifPresent(p -> {
                p.setHiddenAt(null);
                postRepository.save(p);
            });
            case COMMENT -> commentRepository.findById(targetId).filter(c -> c.getHiddenAt() != null).ifPresent(c -> {
                c.setHiddenAt(null);
                commentRepository.save(c);
            });
            case VENUE_REVIEW -> venueReviewRepository.findById(targetId).filter(r -> r.getHiddenAt() != null)
                    .ifPresent(r -> {
                        r.setHiddenAt(null);
                        venueReviewRepository.save(r);
                    });
            default -> {
            }
        }
    }

    private AdminReportResponse describe(Report.TargetType type, Long targetId, long count, LocalDateTime last,
            List<Report> reports) {
        Map<String, Long> reasons = new LinkedHashMap<>();
        EnumMap<Report.Reason, Long> counted = new EnumMap<>(Report.Reason.class);
        List<String> details = new ArrayList<>();
        for (Report r : reports) {
            counted.merge(r.getReason(), 1L, Long::sum);
            if (r.getDetail() != null && !r.getDetail().isBlank() && details.size() < MAX_DETAILS) {
                details.add(r.getDetail());
            }
        }
        counted.forEach((reason, n) -> reasons.put(reason.name(), n));

        AdminReportResponse.AdminReportResponseBuilder builder = AdminReportResponse.builder()
                .targetType(type.name())
                .targetId(targetId)
                .reportCount(count)
                .lastReportedAt(last)
                .reasons(reasons)
                .details(details);

        Long authorId = null;
        switch (type) {
            case POST -> {
                Post post = postRepository.findById(targetId).orElse(null);
                if (post == null) {
                    builder.removed(true);
                } else {
                    authorId = post.getUserId();
                    builder.removed(post.getDeletedAt() != null).hidden(post.getHiddenAt() != null)
                            .title(post.getTitle()).snippet(snippet(post.getContent()));
                }
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId).orElse(null);
                if (comment == null) {
                    builder.removed(true);
                } else {
                    authorId = comment.getUserId();
                    builder.removed(comment.getDeletedAt() != null).hidden(comment.getHiddenAt() != null)
                            .snippet(snippet(comment.getContent())).postId(comment.getPostId());
                }
            }
            case VENUE_REVIEW -> {
                VenueReview review = venueReviewRepository.findById(targetId).orElse(null);
                if (review == null) {
                    builder.removed(true);
                } else {
                    authorId = review.getUserId();
                    builder.removed(review.getDeletedAt() != null).hidden(review.getHiddenAt() != null)
                            .snippet(snippet(review.getContent())).venueId(review.getVenueId());
                }
            }
            case USER -> {
                authorId = targetId;
                User user = userRepository.findById(targetId).orElse(null);
                if (user == null || user.getDeletedAt() != null) {
                    builder.removed(true);
                } else {
                    builder.authorBanned(user.getBannedAt() != null).title(user.getNickname());
                }
            }
        }

        if (authorId != null) {
            builder.authorId(authorId);
            userRepository.findById(authorId).ifPresent(u -> {
                builder.authorNickname(u.getNickname());
                if (type != Report.TargetType.USER) {
                    builder.authorBanned(u.getBannedAt() != null);
                }
            });
        }
        return builder.build();
    }

    // 본문 앞부분만 보여준다 (사진 표기는 [사진]으로 줄이고 줄바꿈은 공백으로)
    static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "[사진]").replaceAll("\\s+", " ").trim();
        return text.length() > SNIPPET_LENGTH ? text.substring(0, SNIPPET_LENGTH) + "…" : text;
    }
}
