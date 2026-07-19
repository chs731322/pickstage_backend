package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertDetailResponse {
    
    private Long id;
    private String title;
    private String posterImage;
    private List<String> images;
    private LocalDate startDate;
    private LocalDate endDate;
    private String region;
    private String genre;

    @Getter(onMethod_ = @__(@JsonProperty("isFestival")))
    private boolean isFestival;
    private String description;
    private LocalDateTime ticketOpenAt;
    private String ticketLink;
    private String externalUrl;

    private int viewCount;
    private int bookmarkCount;

    private VenueInfo venue;
    private List<ArtistInfo> artists;
 
}
