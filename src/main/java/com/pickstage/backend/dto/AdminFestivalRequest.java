package com.pickstage.backend.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminFestivalRequest {
    private String title;
    private String description;
    private Long venueId;
    private String venueName;
    private String address;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;
    private String genre;
    private String posterImage;
    private List<String> images;
    private List<TicketRequest> tickets;
    private List<LineupRequest> lineup;
}
