package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VenueReviewInfo {

    private Long id;
    // 후기를 쓴 사용자의 id (신고·차단에 쓴다)
    private Long authorId;
    private String nickname;
    private Integer rating;
    private String content;
    private String viewInfo;
    private String standingTip;
    private String seatSection;
    private LocalDateTime createdAt;
    private List<String> images;
}
