package com.pickstage.backend.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReviewKeywordHighlight {
    private String keyword;
    private int count;
    private String sentiment;
}
