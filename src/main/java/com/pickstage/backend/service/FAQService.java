package com.pickstage.backend.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.repository.FAQRepository;
import com.pickstage.backend.repository.UserRepository;

import com.pickstage.backend.dto.FAQDetailResponse;
import com.pickstage.backend.dto.FAQListResponse;
import com.pickstage.backend.entity.FAQ;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.dto.AdminFAQRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FAQService {

    private final FAQRepository faqRepository;
    private final UserRepository userRepository;

    public FAQService(FAQRepository faqRepository, UserRepository userRepository) {
        this.faqRepository = faqRepository;
        this.userRepository = userRepository;
    }

    // FAQ 목록 조회 (등록/수정/삭제 전까지는 캐시에서 바로 응답)
    @Cacheable("faqs")
    @Transactional(readOnly = true)
    public List<FAQListResponse> getFAQs() {
        List<FAQ> faqs = faqRepository.findAllByOrderBySortOrderAsc();

        return faqs.stream()
                .map(faq -> FAQListResponse.builder()
                        .id(faq.getId())
                        .question(faq.getQuestion())
                        .sortOrder(faq.getSortOrder())
                        .createdAt(faq.getCreatedAt())
                        .updatedAt(faq.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // FAQ 상세 조회
    @Cacheable(value = "faqDetail", key = "#id")
    @Transactional(readOnly = true)
    public FAQDetailResponse getFAQ(Long id) {
        FAQ faq = faqRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 FAQ입니다."));

        return FAQDetailResponse.builder()
                .id(faq.getId())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .createdAt(faq.getCreatedAt())
                .updatedAt(faq.getUpdatedAt())
                .build();
    }

    // 관리자 FAQ 등록
    @CacheEvict(value = { "faqs", "faqDetail" }, allEntries = true)
    @Transactional
    public Long createFAQ(Long requesterId, AdminFAQRequest request) {
        requireAdmin(requesterId);

        FAQ faq = FAQ.builder()
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .sortOrder(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return faqRepository.save(faq).getId();
    }

    // 관리자 FAQ 수정
    @CacheEvict(value = { "faqs", "faqDetail" }, allEntries = true)
    @Transactional
    public void updateFAQ(Long requesterId, Long id, AdminFAQRequest request) {
        requireAdmin(requesterId);

        FAQ faq = faqRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 FAQ입니다."));

        if (request.getQuestion() != null) {
            faq.setQuestion(request.getQuestion());
        }
        if (request.getAnswer() != null) {
            faq.setAnswer(request.getAnswer());
        }
        faq.setUpdatedAt(LocalDateTime.now());

        faqRepository.save(faq);
    }

    // 관리자 FAQ 삭제
    @CacheEvict(value = { "faqs", "faqDetail" }, allEntries = true)
    @Transactional
    public void deleteFAQ(Long requesterId, Long id) {
        requireAdmin(requesterId);

        FAQ faq = faqRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 FAQ입니다."));

        faqRepository.delete(faq);
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
