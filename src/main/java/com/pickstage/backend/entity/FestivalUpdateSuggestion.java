package com.pickstage.backend.entity;

import java.time.LocalDate;
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

// 공연 정보 갱신 제안: 새로 찾은 아티스트 1명 또는 티켓 1개. 관리자가 승인해야 공연에 반영한다.
@Entity
@Table(name = "festival_update_suggestions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FestivalUpdateSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long festivalId;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private Status status;

    // 라인업 제안일 때. artistId 가 null 이면 우리 DB에 아직 없는 아티스트
    private String artistName;
    private Long artistId;
    private LocalDate performanceDate;

    // 티켓 제안일 때
    private String label;
    private String vendorName;
    private Integer price;
    private String bookingUrl;
    private LocalDateTime openAt;

    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private Long handledBy;

    // 관리자가 승인하거나 거절했을 때
    public void handle(Status result, Long adminId) {
        this.status = result;
        this.handledBy = adminId;
        this.handledAt = LocalDateTime.now();
    }

    public enum Type {
        LINEUP, TICKET
    }

    // 승인 대기 / 승인해서 반영함 / 반영하지 않기로 함
    public enum Status {
        PENDING, APPROVED, REJECTED
    }
}
