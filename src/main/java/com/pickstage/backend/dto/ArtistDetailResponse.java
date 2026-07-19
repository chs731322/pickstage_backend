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
    private String profileImage;
    private String genre;
    private String description;
    private List<UpcomingConcertInfo> upcomingConcerts;
}
