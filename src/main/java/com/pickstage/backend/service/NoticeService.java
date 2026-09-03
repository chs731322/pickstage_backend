package com.pickstage.backend.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.AdminNoticeRequest;
import com.pickstage.backend.dto.NoticeDetailResponse;
import com.pickstage.backend.dto.NoticeListResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.entity.Notice;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.NoticeRepository;
import com.pickstage.backend.repository.UserRepository;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    public NoticeService(NoticeRepository noticeRepository, UserRepository userRepository) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
    }

    // 공지사항 목록 조회
    @Transactional(readOnly = true)
    public PageResponse<NoticeListResponse> getNotices(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("올바르지 않은 요청입니다.");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Notice> notices = noticeRepository.findAllOrderByPinnedAndCreatedAtDesc(pageable);

        Page<NoticeListResponse> content = notices.map(notice -> NoticeListResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .isPinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .build());

        return PageResponse.of(content);
    }

    // 공지사항 상세 조회
    @Transactional
    public NoticeDetailResponse getNotice(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공지사항입니다."));

        notice.incrementViewCount();

        return NoticeDetailResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .isPinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }

    // 관리자 공지사항 등록
    @Transactional
    public Long createNotice(Long requesterId, AdminNoticeRequest request) {
        requireAdmin(requesterId);

        Notice notice = Notice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .isPinned(request.getIsPinned() != null && request.getIsPinned())
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return noticeRepository.save(notice).getId();
    }

    // 관리자 공지사항 수정
    @Transactional
    public void updateNotice(Long requesterId, Long id, AdminNoticeRequest request) {
        requireAdmin(requesterId);

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공지사항입니다."));

        if (request.getTitle() != null) {
            notice.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            notice.setContent(request.getContent());
        }
        if (request.getIsPinned() != null) {
            notice.setPinned(request.getIsPinned());
        }
        notice.setUpdatedAt(LocalDateTime.now());

        noticeRepository.save(notice);
    }

    // 관리자 공지사항 삭제
    @Transactional
    public void deleteNotice(Long requesterId, Long id) {
        requireAdmin(requesterId);

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공지사항입니다."));

        noticeRepository.delete(notice);
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
