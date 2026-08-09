package com.pickstage.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReissueRequest {

    @NotBlank(message = "리프레시 토큰을 입력해주세요.")
    private String refreshToken;
}