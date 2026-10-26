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
    // 작성자 프로필 링크용 (탈퇴한 회원이면 null)
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private Long festivalId;
    private String festivalTitle;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    // 본문 앞부분 (사진 줄은 뺀 글만). 메인 페이지 카드처럼 목록에서 본문을 보여줄 때 쓴다
    private String preview;
    private LocalDateTime createdAt;
}
