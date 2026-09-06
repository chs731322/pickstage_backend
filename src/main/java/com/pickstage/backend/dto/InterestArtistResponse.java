package com.pickstage.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterestArtistResponse {

    private Long id;
    private String name;
    private String profileImage;
    private String genre;
}