package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VenueReviewInfo {

    private Long id;
    private String nickname;
    private Integer rating;
    private String content;
    private String viewInfo;
    private String standingTip;
    private String seatSection;
    private LocalDateTime createdAt;
}
