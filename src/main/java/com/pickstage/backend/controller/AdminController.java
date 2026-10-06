package com.pickstage.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.AdminFAQRequest;
import com.pickstage.backend.dto.AdminFestivalRequest;
import com.pickstage.backend.dto.FestivalDraftRequest;
import com.pickstage.backend.dto.FestivalDraftResponse;
import com.pickstage.backend.dto.AdminNoticeRequest;
import com.pickstage.backend.dto.AdminUserResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.RoleUpdateRequest;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.FestivalDraftService;
import com.pickstage.backend.service.FestivalService;
import com.pickstage.backend.service.NoticeService;
import com.pickstage.backend.service.PostService;
import com.pickstage.backend.service.ReviewService;
import com.pickstage.backend.service.UserService;
import com.pickstage.backend.service.FAQService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PostService postService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final FestivalService festivalService;
    private final FestivalDraftService festivalDraftService;
    private final NoticeService noticeService;
    private final FAQService faqService;

    public AdminController(PostService postService, ReviewService reviewService, UserService userService,
                           FestivalService festivalService, NoticeService noticeService, FAQService faqService, FestivalDraftService festivalDraftService) {
        this.postService = postService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.festivalService = festivalService;
        this.festivalDraftService = festivalDraftService;
        this.noticeService = noticeService;
        this.faqService = faqService;
    }

    // 관리자 공지사항 등록
    @PostMapping("/notices")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createNotice(
            @AuthenticationPrincipal Long userId, @RequestBody AdminNoticeRequest request) {
        Long noticeId = noticeService.createNotice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("id", noticeId), "공지사항이 등록되었습니다."));
    }

    // 관리자 공지사항 수정
    @PatchMapping("/notices/{id}")
    public ResponseEntity<ApiResponse<Void>> updateNotice(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody AdminNoticeRequest request) {
        noticeService.updateNotice(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("공지사항이 수정되었습니다."));
    }

    // 관리자 공지사항 삭제
    @DeleteMapping("/notices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        noticeService.deleteNotice(userId, id);
        return ResponseEntity.ok(ApiResponse.success("공지사항이 삭제되었습니다."));
    }

    // 관리자 FAQ 등록
    @PostMapping("/faqs")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createFaq(
            @AuthenticationPrincipal Long userId, @RequestBody AdminFAQRequest request) {
        Long faqId = faqService.createFAQ(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("id", faqId), "FAQ가 등록되었습니다."));
    }

    // 관리자 FAQ 수정
    @PatchMapping("/faqs/{id}")
    public ResponseEntity<ApiResponse<Void>> updateFaq(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody AdminFAQRequest request) {
        faqService.updateFAQ(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("FAQ가 수정되었습니다."));
    }

    // 관리자 FAQ 삭제
    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        faqService.deleteFAQ(userId, id);
        return ResponseEntity.ok(ApiResponse.success("FAQ가 삭제되었습니다."));
    }

    // 관리자 공연 등록
    @PostMapping("/festivals")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createFestival(
            @AuthenticationPrincipal Long userId, @RequestBody AdminFestivalRequest request) {
        Long festivalId = festivalService.createFestival(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("id", festivalId), "공연이 등록되었습니다."));
    }

    // 관리자 공연 등록 초안 생성 (AI): 공고 텍스트/포스터 이미지 -> 초안. 저장은 하지 않는다.
    @PostMapping("/festivals/ai-draft")
    public ResponseEntity<ApiResponse<FestivalDraftResponse>> createFestivalDraft(
            @AuthenticationPrincipal Long userId, @RequestBody FestivalDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.success(festivalDraftService.createDraft(userId, request)));
    }

    // 관리자 공연 수정
    @PatchMapping("/festivals/{id}")
    public ResponseEntity<ApiResponse<Void>> updateFestival(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestBody AdminFestivalRequest request) {
        festivalService.updateFestival(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("공연 정보가 수정되었습니다."));
    }

    // 관리자 공연 삭제
    @DeleteMapping("/festivals/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFestival(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        festivalService.deleteFestival(userId, id);
        return ResponseEntity.ok(ApiResponse.success("공연이 삭제되었습니다."));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AdminUserResponse> response = userService.getUsers(userId, keyword, role, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> forceDeletePost(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        postService.forceDeletePost(userId, id);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다."));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> forceDeleteReview(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        reviewService.forceDeleteReview(userId, id);
        return ResponseEntity.ok(ApiResponse.success("후기가 삭제되었습니다."));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        userService.updateRole(userId, id, request);

        return ResponseEntity.ok(ApiResponse.success("회원 권한이 변경되었습니다."));
    }
}