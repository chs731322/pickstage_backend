package com.pickstage.backend.dto;

import java.time.LocalDate;
import java.util.List;

// 날씨 요약: 오늘(또는 중기예보의 가장 가까운 날)의 예보와, 이어지는 며칠의 간단한 예보.
// 단기예보(source = "short")는 오늘~모레, 중기예보(source = "mid")는 3~10일 뒤 하루만 준다.
public record WeatherSummary(
        LocalDate date,
        int dayOffset,
        Integer tempMin,
        Integer tempMax,
        // 맑음 / 구름많음 / 흐림 / 비 / 눈 등 (알 수 없으면 null)
        String sky,
        boolean isRain,
        String source,
        List<Day> upcoming) {

    // 오늘 이후의 하루치 예보
    public record Day(
            LocalDate date,
            int dayOffset,
            Integer tempMin,
            Integer tempMax,
            String sky,
            boolean isRain) {
    }
}
