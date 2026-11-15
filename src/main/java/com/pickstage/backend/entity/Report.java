package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 올린 신고. 한 사람은 같은 대상을 한 번만 신고할 수 있다 (DB 유니크 키).
@Entity
@Table(name = "reports")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reporterId;

    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    private Long targetId;

    @Enumerated(EnumType.STRING)
    private Reason reason;

    // 자세한 설명 (선택, 최대 200자)
    private String detail;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private Long handledBy;

    public enum TargetType {
        POST, COMMENT, VENUE_REVIEW, USER
    }

    // 스팸·광고 / 욕설·혐오 / 음란·선정 / 불법·사기 / 개인정보 노출 / 기타
    public enum Reason {
        SPAM, ABUSE, SEXUAL, ILLEGAL, PRIVACY, OTHER
    }

    // 처리 전 / 신고 대상을 삭제함 / 문제 없음으로 기각
    public enum Status {
        PENDING, DELETED, DISMISSED
    }
}
