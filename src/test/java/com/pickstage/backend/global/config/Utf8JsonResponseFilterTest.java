package com.pickstage.backend.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

class Utf8JsonResponseFilterTest {

    private final Utf8JsonResponseFilter filter = new Utf8JsonResponseFilter();

    // 필터를 거친 응답에 대해 handler가 하는 일을 실행하고, 최종 Content-Type을 돌려준다
    private String run(java.util.function.Consumer<HttpServletResponse> handler) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> handler.accept((HttpServletResponse) res));
        return response.getHeader("Content-Type");
    }

    @Test
    void JSON_응답에는_charset이_붙는다() throws Exception {
        assertEquals("application/json;charset=UTF-8", run(res -> res.setContentType("application/json")));
    }

    @Test
    void 헤더로_Content_Type을_써도_붙는다() throws Exception {
        assertEquals("application/json;charset=UTF-8", run(res -> res.setHeader("Content-Type", "application/json")));
    }

    @Test
    void 응답을_초기화한_뒤_다시_써도_붙는다() throws Exception {
        // 예외 처리 중에는 응답이 reset 되고 Content-Type을 새로 쓴다
        assertEquals("application/json;charset=UTF-8", run(res -> {
            res.setContentType("application/json;charset=UTF-8");
            res.reset();
            res.setContentType("application/json");
        }));
    }

    @Test
    void 이미_charset이_있거나_JSON이_아니면_그대로_둔다() throws Exception {
        assertEquals("application/json;charset=UTF-8", run(res -> res.setContentType("application/json;charset=UTF-8")));
        assertEquals("image/png", run(res -> res.setContentType("image/png")));
        assertNull(run(res -> {
        }));
    }
}
