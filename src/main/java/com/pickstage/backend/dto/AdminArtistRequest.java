package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 항목만 바꾼다. 빈 문자열("")은 값을 비운다. (이름은 비울 수 없다)
@Getter
@NoArgsConstructor
public class AdminArtistRequest {

    private String name;
    private String englishName;
    private String artistType;
    private String genre;
    private String profileImage;
    private String officialUrl;
    private String instagramUrl;
    private String spotifyUrl;
}
