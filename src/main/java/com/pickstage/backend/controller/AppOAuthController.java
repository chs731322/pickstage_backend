package com.pickstage.backend.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.pickstage.backend.dto.AppTicketRequest;
import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.AppLoginTicketStore;
import com.pickstage.backend.service.AuthService;
import com.pickstage.backend.service.GoogleService;
import com.pickstage.backend.service.KakaoService;

import jakarta.validation.Valid;

// 모바일 앱의 카카오·구글 로그인.
//   1) 앱이 /{provider}/start 를 연다 → 서버가 소셜 로그인 화면으로 보낸다
//   2) 로그인이 끝나면 소셜 서비스가 /{provider}/callback 으로 돌아온다 → 서버가 로그인·가입을 처리하고,
//      앱 주소(pickstage://oauth)로 1회용 티켓을 붙여 보낸다
//   3) 앱이 /ticket 으로 티켓을 보내 토큰을 받는다
// 토큰을 주소에 싣지 않으려고 티켓을 한 번 거친다.
@RestController
@RequestMapping("/api/auth/app")
public class AppOAuthController {

    private static final Set<String> PROVIDERS = Set.of("kakao", "google");
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9_-]{8,128}");

    private final AuthService authService;
    private final KakaoService kakaoService;
    private final GoogleService googleService;
    private final AppLoginTicketStore ticketStore;

    // 서버의 공개 주소 (소셜 서비스 콘솔에 등록하는 돌아올 주소의 앞부분)
    @Value("${app.public-base-url:https://pickstagebackend-production.up.railway.app}")
    private String publicBaseUrl;

    // 로그인이 끝나면 앱으로 돌아갈 주소
    @Value("${app.oauth-return-url:pickstage://oauth}")
    private String returnUrl;

    public AppOAuthController(AuthService authService, KakaoService kakaoService, GoogleService googleService,
            AppLoginTicketStore ticketStore) {
        this.authService = authService;
        this.kakaoService = kakaoService;
        this.googleService = googleService;
        this.ticketStore = ticketStore;
    }

    // 소셜 콘솔(카카오 Redirect URI, 구글 승인된 리디렉션 URI)에 등록해야 하는 주소
    String callbackUri(String provider) {
        return publicBaseUrl + "/api/auth/app/" + provider + "/callback";
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable String provider, @RequestParam String state) {
        requireProvider(provider);
        if (!STATE.matcher(state).matches()) {
            throw new IllegalArgumentException("올바르지 않은 요청입니다.");
        }
        String url = "kakao".equals(provider)
                ? kakaoService.authorizeUrl(callbackUri(provider), state)
                : googleService.authorizeUrl(callbackUri(provider), state);
        return redirect(url);
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        requireProvider(provider);
        String safeState = state != null && STATE.matcher(state).matches() ? state : null;

        // 사용자가 로그인 창에서 취소했거나 소셜 서비스가 오류를 돌려준 경우
        if (error != null || code == null || code.isBlank()) {
            return redirect(returnTo(null, safeState, "로그인을 취소했거나 완료하지 못했어요."));
        }

        try {
            String redirectUri = callbackUri(provider);
            AuthResponse response = "kakao".equals(provider)
                    ? authService.kakaoLogin(code, redirectUri)
                    : authService.googleLogin(code, redirectUri);
            return redirect(returnTo(ticketStore.issue(response), safeState, null));
        } catch (IllegalArgumentException e) {
            // 정지·탈퇴, 이미 다른 방식으로 가입된 이메일 같은 안내 문구는 그대로 앱에 보여준다
            return redirect(returnTo(null, safeState, e.getMessage()));
        } catch (RuntimeException e) {
            return redirect(returnTo(null, safeState, "로그인 중 오류가 발생했어요. 잠시 후 다시 시도해주세요."));
        }
    }

    @PostMapping("/ticket")
    public ResponseEntity<ApiResponse<AuthResponse>> redeem(@Valid @RequestBody AppTicketRequest request) {
        AuthResponse response = ticketStore.redeem(request.getTicket())
                .orElseThrow(() -> new IllegalArgumentException("로그인 정보가 만료되었어요. 다시 시도해주세요."));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    String returnTo(String ticket, String state, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(returnUrl);
        if (ticket != null) {
            builder.queryParam("ticket", ticket);
        }
        if (state != null) {
            builder.queryParam("state", state);
        }
        if (error != null) {
            builder.queryParam("error", error);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    private void requireProvider(String provider) {
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다.");
        }
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, URI.create(url).toString()).build();
    }
}
