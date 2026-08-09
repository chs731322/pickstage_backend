package com.pickstage.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReissueResponse {

    private String accessToken;
    private String refreshToken;
}