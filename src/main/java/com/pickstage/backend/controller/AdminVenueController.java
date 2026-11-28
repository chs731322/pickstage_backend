package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.entity.User;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.service.KopisVenueService;

// 관리자 공연 등록 폼: 우리 목록에 없는 공연장의 주소·지역을 KOPIS에서 미리 찾아 보여준다 (저장은 하지 않는다)
@RestController
@RequestMapping("/api/admin/venues")
public class AdminVenueController {

    private final KopisVenueService kopisVenueService;
    private final UserRepository userRepository;

    public AdminVenueController(KopisVenueService kopisVenueService, UserRepository userRepository) {
        this.kopisVenueService = kopisVenueService;
        this.userRepository = userRepository;
    }

    // 못 찾으면 data 가 비어 있다
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<KopisVenueService.KopisVenue>> lookup(
            @AuthenticationPrincipal Long userId, @RequestParam String name) {
        boolean admin = userRepository.findById(userId)
                .map(user -> user.getRole() == User.Role.admin)
                .orElse(false);
        if (!admin) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(kopisVenueService.find(name).orElse(null)));
    }
}
