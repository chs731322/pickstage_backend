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
}
