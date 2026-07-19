package com.pickstage.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FestivalListResponse {

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
