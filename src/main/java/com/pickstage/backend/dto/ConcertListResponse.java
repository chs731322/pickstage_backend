package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertListResponse {

    private Long id;
    private String title;
    private String posterImage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String region;
    private String genre;

    @Getter(onMethod_ = @__(@JsonProperty("isFestival")))
    private boolean isFestival;
    private String venueName;
    private int bookmarkCount;

}
