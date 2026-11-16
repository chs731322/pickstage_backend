package com.pickstage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pickstage.backend.dto.WeatherSummary;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.WeatherService;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    // 날씨 요약 (서울 기준). 로그인 없이 볼 수 있다.
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<WeatherSummary>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getSummary()));
    }
}
