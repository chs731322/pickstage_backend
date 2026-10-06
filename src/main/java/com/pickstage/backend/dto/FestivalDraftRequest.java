package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 공연 등록 초안 요청: 공고 텍스트, 포스터 이미지(base64) 중 하나 이상
@Getter
@NoArgsConstructor
public class FestivalDraftRequest {
    private String text;
    private String imageBase64;
    private String imageMimeType;
}
