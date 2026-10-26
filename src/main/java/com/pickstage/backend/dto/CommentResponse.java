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
    // 작성자 프로필 링크용 (탈퇴한 회원이면 null)
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private LocalDateTime createdAt;
}
