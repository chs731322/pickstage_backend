package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueDetailResponse {

    private Long id;
    private String name;
    private String address;
    private String region;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer capacity;
    private String transportInfo;
    private String facilities;
    private List<VenueImageInfo> images;
    private List<VenueUpcomingConcertInfo> upcomingConcerts;
    private List<VenueReviewInfo> reviews;
}
