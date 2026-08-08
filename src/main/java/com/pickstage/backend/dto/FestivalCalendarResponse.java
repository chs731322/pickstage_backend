package com.pickstage.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalCalendarResponse {
    public Long id;
    public String title;
    public String startDate;
    public String endDate;
    public String region;
    public String posterImage;
    public String venueName;
}
