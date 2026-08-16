package com.pickstage.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MyPageUpdateRequest {

    @Size(
            max = 20,
            message = "닉네임은 최대 20자까지 입력할 수 있습니다."
    )
    private String nickname;

    private String profileImage;
}