package com.pickstage.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.dto.GoogleTokenResponse;
import com.pickstage.backend.dto.GoogleUserResponse;

@Service
public class GoogleService {

    private static final String GOOGLE_TOKEN_URL =
            "https://oauth2.googleapis.com/token";

    private static final String GOOGLE_USER_INFO_URL =
            "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    public GoogleService() {
        this.restClient = RestClient.create();
    }

    // Access Token 발급
    public String getAccessToken(String code) {

        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        try {
            GoogleTokenResponse response =
                    restClient.post()
                            .uri(GOOGLE_TOKEN_URL)
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .body(body)
                            .retrieve()
                            .body(GoogleTokenResponse.class);

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
                    "구글 서버와 통신 중 오류가 발생했습니다."
            );

        } catch (RestClientException e) {

            throw new RuntimeException(
                    "구글 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }

    //Access Token으로 정보 조회
    public GoogleUserResponse getUserInfo(
            String accessToken) {

        try {
            GoogleUserResponse response =
                    restClient.get()
                            .uri(GOOGLE_USER_INFO_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .retrieve()
                            .body(GoogleUserResponse.class);

            if (response == null
                    || response.getSub() == null) {

                throw new RuntimeException(
                        "구글 서버와 통신 중 오류가 발생했습니다."
                );
            }

            return response;

        } catch (RestClientException e) {

            throw new RuntimeException(
                    "구글 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }
}