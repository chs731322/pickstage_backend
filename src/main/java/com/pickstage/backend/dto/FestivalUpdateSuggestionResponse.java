package com.pickstage.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.pickstage.backend.entity.FestivalUpdateSuggestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 관리자 화면에 보여주는 갱신 제안 1건
@Getter
@Builder
@AllArgsConstructor
public class FestivalUpdateSuggestionResponse {

    private Long id;
    private Long festivalId;
    private String festivalTitle;
    private String type;
    private String status;

    private String artistName;
    private boolean newArtist; // 우리 DB에 아직 없는 아티스트
    private LocalDate performanceDate;

    private String label;
    private String vendorName;
    private Integer price;
    private String bookingUrl;
    private LocalDateTime openAt;

    private LocalDateTime createdAt;

    public static FestivalUpdateSuggestionResponse of(FestivalUpdateSuggestion s, String festivalTitle) {
        return FestivalUpdateSuggestionResponse.builder()
                .id(s.getId())
                .festivalId(s.getFestivalId())
                .festivalTitle(festivalTitle)
                .type(s.getType().name())
                .status(s.getStatus().name())
                .artistName(s.getArtistName())
                .newArtist(s.getType() == FestivalUpdateSuggestion.Type.LINEUP && s.getArtistId() == null)
                .performanceDate(s.getPerformanceDate())
                .label(s.getLabel())
                .vendorName(s.getVendorName())
                .price(s.getPrice())
                .bookingUrl(s.getBookingUrl())
                .openAt(s.getOpenAt())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
