package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyReviewResponse {

    private Long id;
    private String venueName;
    private int rating;
    private String content;
    private String viewInfo;
    private String standingTip;
    private String seatSection;
    private LocalDateTime createdAt;
}