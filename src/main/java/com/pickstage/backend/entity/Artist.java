package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_id")
    private Long id;

    private String name;

    // 공백 제거 + 소문자 (중복 아티스트 방지용 UNIQUE 키)
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "english_name")
    private String englishName;

    @Column(name = "artist_type")
    private String artistType;

    @Column(name = "genre_primary")
    private String genrePrimary;

    @Column(name = "genre_tags")
    private String genreTags;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "official_url")
    private String officialUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "spotify_url")
    private String spotifyUrl;

    // 관리자 수정: 이름과 함께 중복 방지 키(normalizedName)도 같이 바꾼다
    public void rename(String name, String normalizedName) {
        this.name = name;
        this.normalizedName = normalizedName;
    }

    // 관리자 수정: 이름 외의 정보를 한꺼번에 바꾼다 (null은 비우기)
    public void updateDetails(String englishName, String artistType, String genrePrimary,
            String profileImageUrl, String officialUrl, String instagramUrl, String spotifyUrl) {
        this.englishName = englishName;
        this.artistType = artistType;
        this.genrePrimary = genrePrimary;
        this.profileImageUrl = profileImageUrl;
        this.officialUrl = officialUrl;
        this.instagramUrl = instagramUrl;
        this.spotifyUrl = spotifyUrl;
    }

    // 합치기: 이쪽에 비어 있는 정보만 합쳐지는 아티스트의 값으로 채운다
    public void fillMissingFrom(Artist other) {
        if (isBlank(englishName)) englishName = other.englishName;
        if (isBlank(artistType)) artistType = other.artistType;
        if (isBlank(genrePrimary)) genrePrimary = other.genrePrimary;
        if (isBlank(profileImageUrl)) profileImageUrl = other.profileImageUrl;
        if (isBlank(officialUrl)) officialUrl = other.officialUrl;
        if (isBlank(instagramUrl)) instagramUrl = other.instagramUrl;
        if (isBlank(spotifyUrl)) spotifyUrl = other.spotifyUrl;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
