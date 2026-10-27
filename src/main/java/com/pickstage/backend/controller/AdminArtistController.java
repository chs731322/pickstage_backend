package com.pickstage.backend.controller;

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

import com.pickstage.backend.dto.AdminArtistMergeRequest;
import com.pickstage.backend.dto.AdminArtistRequest;
import com.pickstage.backend.dto.AdminArtistResponse;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AdminArtistService;
import com.pickstage.backend.service.SpotifyService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/artists")
public class AdminArtistController {

    private final AdminArtistService adminArtistService;

    public AdminArtistController(AdminArtistService adminArtistService) {
        this.adminArtistService = adminArtistService;
    }

    // 아티스트 목록·검색 (unlinkedOnly=true면 공연에 연결되지 않은 아티스트만)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminArtistResponse>>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean unlinkedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminArtistService.list(userId, keyword, unlinkedOnly, page, size)));
    }

    // 아티스트 정보 수정 (보낸 항목만 바뀐다)
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminArtistResponse>> update(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @RequestBody AdminArtistRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminArtistService.update(userId, id, request), "아티스트 정보가 수정되었습니다."));
    }

    // 아티스트 삭제 (공연 라인업에 연결돼 있으면 거절)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        adminArtistService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("아티스트가 삭제되었습니다."));
    }

    // Spotify에서 이 아티스트의 후보(이름·사진·주소)를 찾는다. 고른 사진은 수정(PATCH)으로 저장한다.
    @GetMapping("/{id}/spotify-candidates")
    public ResponseEntity<ApiResponse<List<SpotifyService.ArtistCandidate>>> spotifyCandidates(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.success(adminArtistService.findSpotifyCandidates(userId, id, query)));
    }

    // 중복 아티스트 합치기: 경로의 아티스트를 targetId 아티스트로 합친다
    @PostMapping("/{id}/merge")
    public ResponseEntity<ApiResponse<AdminArtistResponse>> merge(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @RequestBody AdminArtistMergeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminArtistService.merge(userId, id, request), "아티스트를 합쳤습니다."));
    }
}
