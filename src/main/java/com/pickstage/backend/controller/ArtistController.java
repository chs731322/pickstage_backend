package com.pickstage.backend.controller;

import com.pickstage.backend.dto.ArtistDetailResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.ArtistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    // 아티스트 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistDetailResponse>> getArtist(@PathVariable Long id) {

        ArtistDetailResponse result = artistService.getArtist(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
