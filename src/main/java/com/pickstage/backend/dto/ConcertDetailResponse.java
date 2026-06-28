package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


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
