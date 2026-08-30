package com.pickstage.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.dto.KakaoTokenResponse;
import com.pickstage.backend.dto.KakaoUserResponse;

@Service
public class KakaoService {

    private static final String KAKAO_TOKEN_URL =
            "https://kauth.kakao.com/oauth/token";

    private static final String KAKAO_USER_INFO_URL =
            "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    public KakaoService() {
        this.restClient = RestClient.create();
    }

    // 카카오 인가코드로 Access Token 발급
    public String getAccessToken(String code) {

        MultiValueMap<String, String> formData =
                new LinkedMultiValueMap<>();

        formData.add(
                "grant_type",
                "authorization_code"
        );

        formData.add(
                "client_id",
                clientId
        );

        formData.add(
                "client_secret",
                clientSecret
        );

        formData.add(
                "redirect_uri",
                redirectUri
        );

        formData.add(
                "code",
                code
        );

        try {
            KakaoTokenResponse response =
                    restClient.post()
                            .uri(KAKAO_TOKEN_URL)
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .body(formData)
                            .retrieve()
                            .body(KakaoTokenResponse.class);

            if (response == null
                    || response.getAccessToken() == null) {

                throw new IllegalArgumentException(
                        "유효하지 않은 인가코드입니다."
                );
            }

            return response.getAccessToken();

        } catch (RestClientResponseException e) {

            if (e.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(
                        "유효하지 않은 인가코드입니다."
                );
            }

            throw new RuntimeException(
                    "카카오 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }

    // 카카오 Access Token으로 사용자 정보 조회
    public KakaoUserResponse getUserInfo(
            String accessToken) {

        try {
            KakaoUserResponse response =
                    restClient.get()
                            .uri(KAKAO_USER_INFO_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .retrieve()
                            .body(KakaoUserResponse.class);

            if (response == null
                    || response.getId() == null) {

                throw new RuntimeException(
                        "카카오 서버와 통신 중 오류가 발생했습니다."
                );
            }

            return response;

        } catch (RestClientResponseException e) {

            throw new RuntimeException(
                    "카카오 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }
}