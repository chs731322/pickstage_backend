package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyPageResponse {

    private Long id;
    private String email;
    private String nickname;
    private String profileImage;
    private String role;
    private LocalDateTime createdAt;
}