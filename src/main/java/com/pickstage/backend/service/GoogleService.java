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

    @Value("${google.client-id:}")
    private String clientId;

    @Value("${google.client-secret:}")
    private String clientSecret;

    @Value("${google.redirect-uri:}")
    private String redirectUri;

    public GoogleService() {
        this.restClient = RestClient.create();
    }

    // Access Token 발급
    public String getAccessToken(String code) {
        return getAccessToken(code, redirectUri);
    }

    // 웹과 앱은 돌아오는 주소(redirect URI)가 달라서, 인가코드를 받은 때와 같은 주소로 교환해야 한다
    public String getAccessToken(String code, String redirectUri) {

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

    // 구글 로그인 화면 주소 (앱용: 돌아올 주소와 state를 받는다)
    public String authorizeUrl(String redirectUri, String state) {
        return org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }
}