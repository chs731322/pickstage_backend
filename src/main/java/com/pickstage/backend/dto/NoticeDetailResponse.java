package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeDetailResponse {
    private Long id;
    private String title;
    private String content;

    @Getter(onMethod_ = @__(@JsonProperty("isPinned")))
    private boolean isPinned;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
