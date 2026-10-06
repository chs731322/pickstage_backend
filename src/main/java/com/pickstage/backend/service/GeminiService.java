package com.pickstage.backend.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.ObjectMapper;

import com.pickstage.backend.dto.GeminiGenerateContentResponse;
import com.pickstage.backend.dto.ReviewKeywordHighlight;

@Service
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.5-flash-lite}")
    private String model;

    public GeminiService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    // 후기 텍스트 목록에서 키워드 하이라이트 추출
    public List<ReviewKeywordHighlight> extractHighlights(String targetLabel, List<String> reviewTexts) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI 요약 기능이 아직 설정되지 않았습니다.");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", buildPrompt(targetLabel, reviewTexts))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "keyword", Map.of("type", "string"),
                                                "count", Map.of("type", "integer"),
                                                "sentiment", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("positive", "negative", "neutral"))),
                                        "required", List.of("keyword", "count", "sentiment")))));

        String json = callForText(requestBody);
        return Arrays.asList(objectMapper.readValue(json, ReviewKeywordHighlight[].class));
    }

    // 공연 등록 초안 등, 스키마를 지정해 JSON 문자열을 받아오는 범용 호출 (이미지는 선택)
    public String generateJson(String prompt, Map<String, Object> responseSchema,
            String imageBase64, String imageMimeType) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI 기능이 아직 설정되지 않았습니다.");
        }

        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (imageBase64 != null && !imageBase64.isBlank()) {
            parts.add(Map.of("inline_data", Map.of("mime_type", imageMimeType, "data", imageBase64)));
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "temperature", 0));

        return callForText(requestBody);
    }

    // Gemini generateContent 호출 후 첫 후보의 텍스트를 반환
    private String callForText(Map<String, Object> requestBody) {
        try {
            GeminiGenerateContentResponse response = restClient.post()
                    .uri(GEMINI_URL, model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);

            if (response == null
                    || response.getCandidates() == null
                    || response.getCandidates().isEmpty()) {
                throw new RuntimeException("AI 서버와 통신 중 오류가 발생했습니다.");
            }

            return response.getCandidates().get(0).getContent().getParts().get(0).getText();

        } catch (RestClientResponseException e) {
            throw new RuntimeException("AI 서버와 통신 중 오류가 발생했습니다.");
        }
    }

    private String buildPrompt(String targetLabel, List<String> reviewTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 '").append(targetLabel).append("'에 대한 방문자 후기 ")
                .append(reviewTexts.size()).append("건입니다.\n\n");

        for (int i = 0; i < reviewTexts.size(); i++) {
            sb.append(i + 1).append(". ").append(reviewTexts.get(i)).append("\n");
        }

        sb.append("\n위 후기들에서 반복적으로 언급되는 키워드를 뽑아 각 키워드별 언급 횟수와 긍정/부정/중립 여부를 정리해줘. ")
                .append("반드시 위 후기에 실제로 등장하는 내용만 근거로 삼고, 후기에 없는 내용은 절대 만들어내지 마. ")
                .append("언급 횟수가 2건 이상인 키워드만 포함해줘.");

        return sb.toString();
    }
}
