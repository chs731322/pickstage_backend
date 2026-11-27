package com.pickstage.backend.global.util;

import java.util.regex.Pattern;

import com.pickstage.backend.service.ViewGuard;

import jakarta.servlet.http.HttpServletRequest;

// 조회수 중복 방지용 조회자 식별값: 봇이면 봇 표시, 로그인했으면 계정, 아니면 접속 IP (프록시 뒤라 X-Forwarded-For 우선)
public final class ViewerKeys {

    // 검색 로봇·링크 미리보기 등 사람이 아닌 접속
    private static final Pattern BOT_USER_AGENT = Pattern.compile(
            "bot|crawl|spider|slurp|facebookexternalhit|kakaotalk-scrap|preview|headless|curl|wget|python-requests",
            Pattern.CASE_INSENSITIVE);

    private ViewerKeys() {
    }

    public static String of(Long userId, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && BOT_USER_AGENT.matcher(userAgent).find()) {
            return ViewGuard.BOT_KEY;
        }
        if (userId != null) {
            return "u" + userId;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip" + ip;
    }
}
