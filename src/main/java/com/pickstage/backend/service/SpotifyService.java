package com.pickstage.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;

import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 관리자가 아티스트의 프로필 사진을 고를 때 Spotify에서 후보를 찾아 준다 (Client Credentials, 읽기 전용).
// Spotify 개발 모드는 호출이 조금만 많아도 하루 정도 차단(429)되므로, 호출 간격을 두고 차단되면 그 시간 동안은 아예 호출하지 않는다.
@Service
public class SpotifyService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyService.class);
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search?q={q}&type=artist&limit=5";
    private static final Duration MIN_INTERVAL = Duration.ofMillis(1500);
    private static final int PREFERRED_IMAGE_WIDTH = 320;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${spotify.client-id:}")
    private String clientId;

    @Value("${spotify.client-secret:}")
    private String clientSecret;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;
    private Instant bannedUntil = Instant.EPOCH;
    private Instant lastCallAt = Instant.EPOCH;

    public SpotifyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Getter
    @Builder
    public static class ArtistCandidate {
        private final String spotifyId;
        private final String name;
        private final String imageUrl;
        private final String spotifyUrl;
    }

    // 이름으로 아티스트 후보를 최대 5명 찾는다
    public synchronized List<ArtistCandidate> searchArtists(String query) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ExternalServiceUnavailableException("Spotify 연동이 아직 설정되지 않았어요. (서버의 SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET 필요)");
        }
        Instant now = Instant.now();
        if (now.isBefore(bannedUntil)) {
            throw new ExternalServiceUnavailableException(banMessage());
        }

        paceCalls();
        try {
            String body = restClient.get()
                    .uri(SEARCH_URL, query)
                    .header("Authorization", "Bearer " + token())
                    .retrieve()
                    .body(String.class);
            return parseCandidates(objectMapper.readTree(body));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                long retryAfter = parseRetryAfter(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After"));
                bannedUntil = Instant.now().plusSeconds(retryAfter);
                log.warn("Spotify 호출 한도 초과(429): {}초 동안 호출을 멈춥니다", retryAfter);
                throw new ExternalServiceUnavailableException(banMessage());
            }
            if (e.getStatusCode().value() == 401) {
                accessToken = null; // 토큰이 만료됐을 수 있어 다음 호출에서 다시 받는다
            }
            log.warn("Spotify 검색 실패: HTTP {}", e.getStatusCode().value());
            throw new ExternalServiceUnavailableException("Spotify에서 검색하지 못했어요. 잠시 후 다시 시도해 주세요.");
        } catch (ExternalServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Spotify 검색 실패: {}", e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException("Spotify에서 검색하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    // 호출 사이에 최소 간격을 둔다
    private void paceCalls() {
        long waitMillis = Duration.between(Instant.now(), lastCallAt.plus(MIN_INTERVAL)).toMillis();
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = Instant.now();
    }

    private String token() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        try {
            String body = restClient.post()
                    .uri(TOKEN_URL)
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            accessToken = json.path("access_token").asString();
            tokenExpiresAt = Instant.now().plusSeconds(json.path("expires_in").asLong(3600));
            return accessToken;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                long retryAfter = parseRetryAfter(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After"));
                bannedUntil = Instant.now().plusSeconds(retryAfter);
                throw new ExternalServiceUnavailableException(banMessage());
            }
            log.warn("Spotify 인증 실패: HTTP {}", e.getStatusCode().value());
            throw new ExternalServiceUnavailableException("Spotify 인증에 실패했어요. 서버에 설정한 키를 확인해 주세요.");
        }
    }

    private String banMessage() {
        long minutes = Math.max(1, Duration.between(Instant.now(), bannedUntil).toMinutes());
        return "Spotify 호출 한도에 걸렸어요. 약 " + (minutes >= 90 ? (minutes / 60) + "시간" : minutes + "분") + " 뒤에 다시 시도해 주세요.";
    }

    // ---- 순수 로직 (테스트 가능) ----

    static long parseRetryAfter(String header) {
        try {
            long seconds = Long.parseLong(header == null ? "" : header.trim());
            return Math.min(Math.max(seconds, 60), 7 * 24 * 3600L);
        } catch (NumberFormatException e) {
            return 3600; // 값을 알 수 없으면 1시간은 쉰다
        }
    }

    static List<ArtistCandidate> parseCandidates(JsonNode root) {
        List<ArtistCandidate> candidates = new ArrayList<>();
        for (JsonNode item : root.path("artists").path("items")) {
            String id = item.path("id").asString("");
            if (id.isBlank()) {
                continue;
            }
            candidates.add(ArtistCandidate.builder()
                    .spotifyId(id)
                    .name(item.path("name").asString(""))
                    .imageUrl(pickImage(item.path("images")))
                    .spotifyUrl(item.path("external_urls").path("spotify").asString(null))
                    .build());
        }
        return candidates;
    }

    // 사진이 여러 크기로 오면 320px에 가장 가까운 것을 쓴다 (이미 저장된 사진들과 같은 크기)
    static String pickImage(JsonNode images) {
        String best = null;
        long bestGap = Long.MAX_VALUE;
        for (JsonNode image : images) {
            String url = image.path("url").asString("");
            if (url.isBlank()) {
                continue;
            }
            long width = image.path("width").asLong(PREFERRED_IMAGE_WIDTH);
            long gap = Math.abs(width - PREFERRED_IMAGE_WIDTH);
            if (gap < bestGap) {
                best = url;
                bestGap = gap;
            }
        }
        return best;
    }
}
