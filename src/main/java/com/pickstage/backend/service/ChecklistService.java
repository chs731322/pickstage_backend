package com.pickstage.backend.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Checklist;
import com.pickstage.backend.entity.ChecklistTemplate;
import com.pickstage.backend.repository.ChecklistRepository;
import com.pickstage.backend.repository.ChecklistTemplateRepository;

@Service
public class ChecklistService {

    private final ChecklistTemplateRepository checklistTemplateRepository;
    private final ChecklistRepository checklistRepository;

    public ChecklistService(ChecklistTemplateRepository checklistTemplateRepository,
            ChecklistRepository checklistRepository) {
        this.checklistTemplateRepository = checklistTemplateRepository;
        this.checklistRepository = checklistRepository;
    }

    // 체크리스트 조회 (최초 조회 시 템플릿 복사)
    @Transactional
    public List<Checklist> getChecklistByUserId(Long userId) {
        seedTemplateIfNeeded(userId);
        return checklistRepository.findByUserIdOrderBySortOrderAsc(userId);
    }

    // 유저의 체크리스트가 없으면 템플릿을 복사해서 채워줌
    private void seedTemplateIfNeeded(Long userId) {
        if (!checklistRepository.existsByUserId(userId)) {
            List<ChecklistTemplate> templates = checklistTemplateRepository.findAllByOrderBySortOrderAsc();
            for (ChecklistTemplate template : templates) {
                Checklist checklist = new Checklist();
                checklist.setUserId(userId);
                checklist.setContent(template.getContent());
                checklist.setIsChecked(false);
                checklist.setIsCustom(false);
                checklist.setSortOrder(template.getSortOrder());
                checklistRepository.save(checklist);
            }
        }
    }

    // 체크리스트 항목 추가
    @Transactional
    public Checklist addChecklistItem(Long userId, String content) {
        // content가 null이거나 공백인 경우 예외 처리
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("체크리스트 항목 내용은 비어 있을 수 없습니다.");
        }

        seedTemplateIfNeeded(userId);

        int maxSortOrder = checklistRepository.findByUserIdOrderBySortOrderAsc(userId).stream()
                .mapToInt(Checklist::getSortOrder)
                .max()
                .orElse(0);

        Checklist checklist = new Checklist();
        checklist.setUserId(userId);
        checklist.setContent(content);
        checklist.setIsChecked(false);
        checklist.setIsCustom(true);
        checklist.setSortOrder(maxSortOrder + 1);
        return checklistRepository.save(checklist);
    }

    // 체크리스트 항목 수정 (내용/체크 여부 중 전달된 값만 반영)
    public Checklist updateChecklistItem(Long checklistId, Long userId, String content, Boolean isChecked) {
        Checklist checklist = findOwnedChecklist(checklistId, userId, "수정 권한이 없습니다.");

        if (content != null) {
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("체크리스트 항목 내용은 비어 있을 수 없습니다.");
            }
            checklist.setContent(content);
        }
        if (isChecked != null) {
            checklist.setIsChecked(isChecked);
        }
        return checklistRepository.save(checklist);
    }

    // 체크리스트 항목 삭제
    public void deleteChecklistItem(Long checklistId, Long userId) {
        Checklist checklist = findOwnedChecklist(checklistId, userId, "삭제 권한이 없습니다.");
        checklistRepository.delete(checklist);
    }

    // 항목 조회 + 본인 소유 확인 공통 로직
    private Checklist findOwnedChecklist(Long checklistId, Long userId, String forbiddenMessage) {
        Checklist checklist = checklistRepository.findById(checklistId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 체크리스트 항목입니다."));

        if (!checklist.getUserId().equals(userId)) {
            throw new AccessDeniedException(forbiddenMessage);
        }
        return checklist;
    }
}