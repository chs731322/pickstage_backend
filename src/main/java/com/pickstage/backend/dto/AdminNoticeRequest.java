package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminNoticeRequest {
    private String title;
    private String content;
    private Boolean isPinned;
}
