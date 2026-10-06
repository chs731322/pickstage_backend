package com.pickstage.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    // 티켓 오픈 일시와 첫 번째 티켓 이름 (홈의 티켓 오픈 일정용. ticketLabel은 ticket_open 계열 정렬에서만 채움)
    private LocalDateTime ticketOpenAt;
    private String ticketLabel;
}
