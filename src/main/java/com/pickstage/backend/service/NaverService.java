package com.pickstage.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.dto.NaverTokenResponse;
import com.pickstage.backend.dto.NaverUserResponse;

@Service
public class NaverService {

    private static final String NAVER_USER_INFO_URL =
            "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;

    @Value("${naver.client-id}")
    private String clientId;

    @Value("${naver.client-secret}")
    private String clientSecret;

    public NaverService() {
        this.restClient = RestClient.create();
    }

    // 네이버 인가코드로 Access Token 발급
    public String getAccessToken(
            String code,
            String state) {

        try {
            NaverTokenResponse response =
                    restClient.get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .scheme("https")
                                            .host("nid.naver.com")
                                            .path("/oauth2.0/token")
                                            .queryParam(
                                                    "grant_type",
                                                    "authorization_code"
                                            )
                                            .queryParam(
                                                    "client_id",
                                                    clientId
                                            )
                                            .queryParam(
                                                    "client_secret",
                                                    clientSecret
                                            )
                                            .queryParam(
                                                    "code",
                                                    code
                                            )
                                            .queryParam(
                                                    "state",
                                                    state
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(NaverTokenResponse.class);

            if (response == null
                    || response.getAccessToken() == null
                    || response.getError() != null) {

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
                    "네이버 서버와 통신 중 오류가 발생했습니다."
            );

        } catch (RestClientException e) {

            throw new RuntimeException(
                    "네이버 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }

    // 네이버 Access Token으로 사용자 정보 조회
    public NaverUserResponse getUserInfo(
            String accessToken) {

        try {
            NaverUserResponse response =
                    restClient.get()
                            .uri(NAVER_USER_INFO_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .retrieve()
                            .body(NaverUserResponse.class);

            if (response == null
                    || response.getResponse() == null
                    || response.getResponse().getId() == null) {

                throw new RuntimeException(
                        "네이버 서버와 통신 중 오류가 발생했습니다."
                );
            }

            return response;

        } catch (RestClientException e) {

            throw new RuntimeException(
                    "네이버 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }
}