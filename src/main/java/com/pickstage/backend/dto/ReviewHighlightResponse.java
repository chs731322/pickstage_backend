package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewHighlightResponse {
    private int reviewCount;
    private List<ReviewKeywordHighlight> keywords;
    private LocalDateTime updatedAt;
}
