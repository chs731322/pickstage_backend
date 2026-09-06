package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NaverUserResponse {

    private String resultcode;
    private String message;
    private Response response;

    @Getter
    @NoArgsConstructor
    public static class Response {

        private String id;
        private String email;
        private String nickname;

        private String profile_image;
    }
}