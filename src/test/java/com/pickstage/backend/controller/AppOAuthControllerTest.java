package com.pickstage.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.pickstage.backend.dto.AppTicketRequest;
import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.service.AppLoginTicketStore;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.service.GoogleService;
import com.pickstage.backend.service.KakaoService;

// 앱 소셜 로그인 흐름: 시작 → 콜백 → 앱으로 티켓 전달
class AppOAuthControllerTest {

    private static final String BASE = "https://api.example.com";

    private AuthService authService;
    private KakaoService kakaoService;
    private GoogleService googleService;
    private AppLoginTicketStore store;
    private AppOAuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        kakaoService = mock(KakaoService.class);
        googleService = mock(GoogleService.class);
        store = new AppLoginTicketStore();
        controller = new AppOAuthController(authService, kakaoService, googleService, store);
        ReflectionTestUtils.setField(controller, "publicBaseUrl", BASE);
        ReflectionTestUtils.setField(controller, "returnUrl", "pickstage://oauth");
    }

    private String location(ResponseEntity<Void> response) {
        assertEquals(302, response.getStatusCode().value());
        return response.getHeaders().getFirst(HttpHeaders.LOCATION);
    }

    @Test
    void 시작하면_앱용_돌아올_주소로_소셜_로그인_화면에_보낸다() {
        when(kakaoService.authorizeUrl(BASE + "/api/auth/app/kakao/callback", "state1234"))
                .thenReturn("https://kauth.kakao.com/oauth/authorize?x=1");

        assertEquals("https://kauth.kakao.com/oauth/authorize?x=1", location(controller.start("kakao", "state1234")));
    }

    @Test
    void 지원하지_않는_로그인_방식과_이상한_state는_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> controller.start("facebook", "state1234"));
        assertThrows(IllegalArgumentException.class, () -> controller.start("kakao", "bad state!"));
    }

    @Test
    void 로그인에_성공하면_앱으로_티켓과_state를_돌려준다() {
        AuthResponse tokens = AuthResponse.builder().accessToken("a").refreshToken("r").build();
        when(authService.kakaoLogin("code1", BASE + "/api/auth/app/kakao/callback")).thenReturn(tokens);

        String loc = location(controller.callback("kakao", "code1", "state1234", null));

        assertTrue(loc.startsWith("pickstage://oauth?ticket="));
        assertTrue(loc.contains("&state=state1234"));
        String ticket = loc.substring(loc.indexOf("ticket=") + 7, loc.indexOf("&state"));
        assertEquals("a", store.redeem(ticket).orElseThrow().getAccessToken());
    }

    @Test
    void 구글도_같은_방식이다() {
        AuthResponse tokens = AuthResponse.builder().accessToken("g").refreshToken("r").build();
        when(authService.googleLogin("code2", BASE + "/api/auth/app/google/callback")).thenReturn(tokens);

        assertTrue(location(controller.callback("google", "code2", "state1234", null)).contains("ticket="));
    }

    @Test
    void 가입이_막힌_경우_서버의_안내_문구를_앱에_전달한다() {
        when(authService.kakaoLogin("code1", BASE + "/api/auth/app/kakao/callback"))
                .thenThrow(new IllegalArgumentException("이미 다른 방식으로 가입된 이메일입니다."));

        String loc = location(controller.callback("kakao", "code1", "state1234", null));

        assertTrue(loc.startsWith("pickstage://oauth?"));
        assertTrue(loc.contains("error="));
        assertTrue(!loc.contains("ticket="));
    }

    @Test
    void 사용자가_취소하면_오류와_함께_앱으로_돌아간다() {
        String loc = location(controller.callback("kakao", null, "state1234", "access_denied"));
        assertTrue(loc.contains("error="));
        assertTrue(!loc.contains("ticket="));
    }

    @Test
    void 티켓으로_토큰을_받고_한_번_쓰면_못_쓴다() {
        String ticket = store.issue(AuthResponse.builder().accessToken("a").refreshToken("r").build());
        AppTicketRequest request = new AppTicketRequest();
        ReflectionTestUtils.setField(request, "ticket", ticket);

        assertEquals("a", controller.redeem(request).getBody().getData().getAccessToken());
        assertThrows(IllegalArgumentException.class, () -> controller.redeem(request));
    }
}
