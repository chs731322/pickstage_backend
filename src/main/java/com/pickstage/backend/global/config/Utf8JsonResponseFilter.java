package com.pickstage.backend.global.config;

import java.io.IOException;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

// 모든 JSON 응답의 Content-Type을 "application/json;charset=UTF-8"로 내보낸다.
// JSON은 UTF-8이 기본이라 코드(fetch, axios)로 읽을 때는 문제가 없지만, 주소창에서 API를 직접 열어보는 도구는
// charset이 없으면 한글을 CP949 등으로 잘못 읽어 글자가 깨져 보인다 (예: "비"가 "鍮�"로).
// Spring은 예외를 처리하기 전에 응답을 초기화해서 charset 설정이 지워지므로, 설정 대신 Content-Type을 쓰는 순간에 붙인다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Utf8JsonResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, new Utf8JsonResponse(response));
    }

    static String withCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("application/json") && !lower.contains("charset=")) {
            return contentType + ";charset=UTF-8";
        }
        return contentType;
    }

    private static class Utf8JsonResponse extends HttpServletResponseWrapper {

        Utf8JsonResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(withCharset(type));
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, isContentType(name) ? withCharset(value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, isContentType(name) ? withCharset(value) : value);
        }

        private static boolean isContentType(String name) {
            return "Content-Type".equalsIgnoreCase(name);
        }
    }
}
