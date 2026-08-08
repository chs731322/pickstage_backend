package com.pickstage.backend.dto;

import lombok.*;

@Getter
@NoArgsConstructor
public class ReviewUpdateRequest {
    private Integer rating;
    private String content;
    private String viewInfo;
    private String standingTip;
    private String seatSection;
}