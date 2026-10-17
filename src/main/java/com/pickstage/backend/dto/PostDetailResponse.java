package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailResponse {
    private Long id;
    private String category;
    private String title;
    private String content;
    private String authorNickname;
    private String authorProfileImage;
    private Long festivalId;
    private String festivalTitle;
    private int viewCount;
    private int likeCount;
    private boolean liked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> comments;
    private List<String> images;
}
