package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistSearchResponse {
    private Long id;
    private String name;
    private String englishName;
    private String profileImage;
    // 아직 끝나지 않은 공연 중 라인업에 포함된 개수
    private int upcomingCount;
}
