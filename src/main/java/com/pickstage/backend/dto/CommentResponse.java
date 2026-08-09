package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private Long parentId;
    private String content;
    private String authorNickname;
    private LocalDateTime createdAt;
}
