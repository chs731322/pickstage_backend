package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistDetailResponse {

    private Long id;
    private String name;
    private String englishName;
    private String artistType;
    private String profileImage;
    private String genre;
    private List<String> genreTags;
    private String countryCode;
    private String officialUrl;
    private String instagramUrl;
    private String spotifyUrl;
    private String description;
    private List<UpcomingConcertInfo> upcomingConcerts;
    // 출연했던(이미 끝난) 공연. 최근 공연이 먼저 온다.
    private List<UpcomingConcertInfo> pastConcerts;
}
