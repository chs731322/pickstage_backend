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
    private String authorProfileImage;
    private Long festivalId;
    private String festivalTitle;
    private int viewCount;
    private int likeCount;
    private LocalDateTime createdAt;
}
