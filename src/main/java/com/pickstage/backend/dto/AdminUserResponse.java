package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImage;
    private String role;

    @Getter(onMethod_ = @__(@JsonProperty("isWithdrawn")))
    private boolean isWithdrawn;

    @Getter(onMethod_ = @__(@JsonProperty("isBanned")))
    private boolean isBanned;
    private LocalDateTime createdAt;
}
