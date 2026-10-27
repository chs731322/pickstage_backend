package com.pickstage.backend.dto;

import com.pickstage.backend.entity.Artist;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminArtistResponse {

    private Long id;
    private String name;
    private String englishName;
    private String artistType;
    private String genre;
    private String profileImage;
    private String officialUrl;
    private String instagramUrl;
    private String spotifyUrl;
    // 출연 공연 수 / 관심 아티스트로 담은 회원 수
    private long festivalCount;
    private long interestCount;

    public static AdminArtistResponse of(Artist artist, long festivalCount, long interestCount) {
        return AdminArtistResponse.builder()
                .id(artist.getId())
                .name(artist.getName())
                .englishName(artist.getEnglishName())
                .artistType(artist.getArtistType())
                .genre(artist.getGenrePrimary())
                .profileImage(artist.getProfileImageUrl())
                .officialUrl(artist.getOfficialUrl())
                .instagramUrl(artist.getInstagramUrl())
                .spotifyUrl(artist.getSpotifyUrl())
                .festivalCount(festivalCount)
                .interestCount(interestCount)
                .build();
    }
}
