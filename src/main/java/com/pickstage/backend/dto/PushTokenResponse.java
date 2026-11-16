package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenResponse {
    // 이 기기로 푸시를 받는 상태인지 (처음 등록하면 켜짐)
    private boolean enabled;
}
