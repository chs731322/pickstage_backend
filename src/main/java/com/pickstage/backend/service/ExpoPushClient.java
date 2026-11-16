package com.pickstage.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Expo 푸시 서버(https://exp.host/--/api/v2/push/send)에 푸시를 맡긴다. Expo가 iOS는 APNs, Android는 FCM으로 전달한다.
// 한 번에 최대 100개까지 보낼 수 있다. 응답의 "티켓"은 보낸 순서대로 오고, 토큰이 더는 유효하지 않으면 DeviceNotRegistered로 알려준다.
// (Expo 계정의 "향상된 푸시 보안"을 켰다면 서버 환경 변수 EXPO_ACCESS_TOKEN이 필요하다.)
@Component
public class ExpoPushClient {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final String SEND_URL = "https://exp.host/--/api/v2/push/send";

    // 푸시 한 건의 접수 결과
    public record Ticket(boolean ok, String errorCode, String message) {
        public boolean deviceNotRegistered() {
            return "DeviceNotRegistered".equals(errorCode);
        }
    }

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${expo.access-token:}")
    private String accessToken;

    public ExpoPushClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // messages: Expo 푸시 메시지(to, title, body, data ...) 최대 100개. 돌려주는 티켓은 messages와 같은 순서·같은 개수다.
    public List<Ticket> send(List<Map<String, Object>> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (accessToken != null && !accessToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + accessToken);
            }
            String body = request.body(messages).retrieve().body(String.class);
            return parseTickets(objectMapper.readTree(body), messages.size());
        } catch (RestClientResponseException e) {
            log.warn("Expo 푸시 서버 호출 실패: HTTP {}", e.getStatusCode().value());
            throw new ExternalServiceUnavailableException("푸시 서버에 연결하지 못했어요. 잠시 후 다시 시도해주세요.");
        } catch (ExternalServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Expo 푸시 서버 호출 실패: {}", e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException("푸시 서버에 연결하지 못했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    // 응답의 data(티켓 배열)를 읽는다. 요청 전체가 거절되면(errors) 실패로 본다. 개수가 모자라면 나머지는 "알 수 없는 오류"로 채운다.
    static List<Ticket> parseTickets(JsonNode root, int expected) {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            throw new ExternalServiceUnavailableException("푸시 서버가 요청을 거절했어요.");
        }
        List<Ticket> tickets = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            boolean ok = "ok".equals(item.path("status").asString(""));
            tickets.add(new Ticket(ok,
                    ok ? null : item.path("details").path("error").asString(null),
                    ok ? null : item.path("message").asString(null)));
        }
        while (tickets.size() < expected) {
            tickets.add(new Ticket(false, "Unknown", "푸시 서버가 결과를 알려주지 않았어요."));
        }
        return tickets.subList(0, expected);
    }
}
