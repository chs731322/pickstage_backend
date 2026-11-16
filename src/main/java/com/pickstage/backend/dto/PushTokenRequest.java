package com.pickstage.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 푸시 토큰 등록·해제·켜기끄기 요청 (platform은 등록할 때만, enabled는 켜기끄기에서만 쓴다)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenRequest {

    @NotBlank(message = "푸시 토큰이 필요합니다.")
    @Size(max = 255, message = "올바르지 않은 푸시 토큰입니다.")
    private String token;

    // ios / android
    private String platform;

    private Boolean enabled;
}
