package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.ReportCreateRequest;
import com.pickstage.backend.entity.Report;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.ReportRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final VenueReviewRepository venueReviewRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, PostRepository postRepository,
            CommentRepository commentRepository, VenueReviewRepository venueReviewRepository,
            UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.venueReviewRepository = venueReviewRepository;
        this.userRepository = userRepository;
    }

    // 신고 접수
    @Transactional
    public void createReport(Long reporterId, ReportCreateRequest request) {
        // 신고 대상이 있는지 확인하고, 그 글(사용자)의 주인을 찾는다
        Long ownerId = findOwnerId(request.getTargetType(), request.getTargetId());

        if (ownerId.equals(reporterId)) {
            throw new IllegalArgumentException("본인이 작성한 내용은 신고할 수 없습니다.");
        }

        String detail = request.getDetail() == null ? null : request.getDetail().trim();
        if (detail != null && detail.isEmpty()) {
            detail = null;
        }
        if (request.getReason() == Report.Reason.OTHER && detail == null) {
            throw new IllegalArgumentException("기타 사유는 자세한 내용을 입력해주세요.");
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.getTargetType(), request.getTargetId())) {
            throw new DuplicateRequestException("이미 신고한 대상입니다.");
        }

        reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .detail(detail)
                .status(Report.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // 신고 대상의 작성자 id. 없거나 이미 삭제된 대상이면 404
    private Long findOwnerId(Report.TargetType type, Long targetId) {
        return switch (type) {
            case POST -> postRepository.findById(targetId)
                    .filter(p -> p.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 게시글입니다."))
                    .getUserId();
            case COMMENT -> commentRepository.findById(targetId)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 댓글입니다."))
                    .getUserId();
            case VENUE_REVIEW -> venueReviewRepository.findById(targetId)
                    .filter(r -> r.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 후기입니다."))
                    .getUserId();
            case USER -> userRepository.findById(targetId)
                    .filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."))
                    .getId();
        };
    }
}
