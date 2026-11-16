package com.pickstage.backend.controller;

import com.pickstage.backend.dto.ArtistDetailResponse;
import com.pickstage.backend.dto.ArtistSearchResponse;
import com.pickstage.backend.dto.InterestArtistResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ArtistService;
import com.pickstage.backend.service.InterestArtistService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;
    private final InterestArtistService interestArtistService;

    public ArtistController(ArtistService artistService, InterestArtistService interestArtistService) {
        this.artistService = artistService;
        this.interestArtistService = interestArtistService;
    }

    // 내 관심 아티스트 목록 조회
    @GetMapping("/interests")
    public ResponseEntity<ApiResponse<List<InterestArtistResponse>>> getMyInterestArtists(
            @AuthenticationPrincipal Long userId) {

        List<InterestArtistResponse> result =
                interestArtistService.getMyInterestArtists(userId);

        return ResponseEntity.ok(
                ApiResponse.success(result)
        );
    }

    // 공연 검색창에 보여줄 아티스트 후보 (로그인 없이 조회, 출연 공연이 있는 아티스트만)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ArtistSearchResponse>>> searchArtists(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(ApiResponse.success(artistService.searchArtists(keyword, size)));
    }

    // 아티스트 목록 (로그인 없이 조회, 예정된 공연에 출연하는 아티스트만 이름순)
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<ArtistSearchResponse>>> listUpcomingArtists() {
        return ResponseEntity.ok(ApiResponse.success(artistService.listUpcomingArtists()));
    }

    // 사이트맵용 아티스트 id 목록 (로그인 없이 조회, 출연 공연이 있는 아티스트만)
    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<Long>>> getArtistIds() {
        return ResponseEntity.ok(ApiResponse.success(artistService.getArtistIdsForSitemap()));
    }

    // 아티스트 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistDetailResponse>> getArtist(@PathVariable Long id) {

        ArtistDetailResponse result = artistService.getArtist(id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 관심 아티스트 추가
    @PostMapping("/{artistId}")
    public ResponseEntity<ApiResponse<Void>> addInterestArtist(
            @PathVariable Long artistId, @AuthenticationPrincipal Long userId) {
        interestArtistService.addInterestArtist(userId, artistId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("관심 아티스트가 추가되었습니다."));
    }
    
    // 관심 아티스트 삭제
    @DeleteMapping("/{artistId}")
    public ResponseEntity<ApiResponse<Void>> removeInterestArtist(
            @PathVariable Long artistId, @AuthenticationPrincipal Long userId) {
        interestArtistService.deleteInterestArtist(userId, artistId);
        return ResponseEntity.ok(ApiResponse.success("관심 아티스트가 삭제되었습니다."));
    }
}