package com.pickstage.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자 신고 처리 화면의 한 줄: 같은 대상에 들어온 신고를 묶어서 보여준다
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportResponse {
    private String targetType;
    private Long targetId;
    private long reportCount;
    private LocalDateTime lastReportedAt;
    // 사유별 신고 수 (예: {"SPAM": 2, "ABUSE": 1})
    private Map<String, Long> reasons;
    // 신고자가 적은 자세한 내용 (최대 3개)
    private List<String> details;

    // 신고로 임시 숨김 중인지
    private boolean hidden;
    // 대상이 이미 삭제됐거나 없어졌는지
    private boolean removed;
    // 사용자 신고일 때 이미 정지된 계정인지
    private boolean authorBanned;

    // 글 제목 (사용자 신고는 닉네임)
    private String title;
    // 본문 앞부분
    private String snippet;
    private Long authorId;
    private String authorNickname;
    // 댓글 신고일 때 댓글이 달린 게시글, 후기 신고일 때 후기가 달린 공연장
    private Long postId;
    private Long venueId;
}
