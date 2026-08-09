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
    private Long festivalId;
    private String festivalTitle;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentInfo> comments;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentInfo {
        private Long id;
        private Long parentId;
        private String content;
        private String authorNickname;
        private LocalDateTime createdAt;
    }
}
