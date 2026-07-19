package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingConcertInfo {

    private Long id;
    private String title;
    private String posterImage;
    private LocalDate startDate;
    private String region;
    private String venueName;
}
