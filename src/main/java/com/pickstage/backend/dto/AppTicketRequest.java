package com.pickstage.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppTicketRequest {

    // 앱 소셜 로그인이 끝난 뒤 서버가 앱에 돌려준 1회용 티켓
    @NotBlank(message = "로그인 정보가 없습니다.")
    private String ticket;
}
