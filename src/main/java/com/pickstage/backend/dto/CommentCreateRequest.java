package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentCreateRequest {
    private String content;
    private Long parentId;
    private LocalDateTime createdAt;
}
