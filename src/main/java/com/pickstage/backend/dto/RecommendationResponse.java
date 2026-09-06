package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    //id, title, posterImage, startDate, endDate, region, genre, venueName, matchedArtists(List<String>), matchCount
    private Long id;
    private String title;
    private String posterImage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String region;
    private String genre;
    private String venueName;
    private List<String> matchedArtists;
    private int matchCount;
}
