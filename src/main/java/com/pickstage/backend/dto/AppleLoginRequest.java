package com.pickstage.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppleLoginRequest {

    // 앱이 Apple에서 받은 identity token (JWT)
    @NotBlank(message = "애플 로그인 정보가 없습니다.")
    private String identityToken;

    // Apple은 이름을 처음 로그인할 때만 앱에 준다. 닉네임으로 쓴다. (없을 수 있음)
    private String fullName;
}
