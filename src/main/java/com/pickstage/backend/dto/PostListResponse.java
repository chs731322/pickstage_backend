package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostListResponse {
    private Long id;
    private String category;
    private String title;
    private String authorNickname;
    private Long festivalId;
    private String festivalTitle;
    private int viewCount;
    private LocalDateTime createdAt;
}
