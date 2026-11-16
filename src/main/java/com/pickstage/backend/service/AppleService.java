package com.pickstage.backend.service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;

// Apple로 로그인: 앱이 받은 identity token(Apple이 서명한 JWT)이 진짜인지 Apple의 공개키로 확인한다.
// 앱이 직접 받은 토큰을 쓰므로 인가코드 교환이나 client secret(.p8 키)은 필요 없다.
@Service
public class AppleService {

    static final String ISSUER = "https://appleid.apple.com";
    private static final String KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final Duration KEY_CACHE = Duration.ofHours(1);

    // 로그인한 Apple 계정의 정보. email은 처음 로그인할 때만 오고, 비공개 중계 주소일 수 있다.
    public record AppleUser(String subject, String email, boolean emailVerified) {
    }

    // 토큰의 audience로 허용할 값들 (앱 번들 ID. 개발 중 Expo Go는 host.exp.Exponent)
    private final Set<String> allowedAudiences;
    private final Function<String, PublicKey> keyResolver;

    @Autowired
    public AppleService(@Value("${apple.client-ids:}") String clientIds) {
        this.allowedAudiences = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.keyResolver = new CachingAppleKeys(RestClient.create())::find;
    }

    // 테스트용: 공개키를 직접 넘긴다
    AppleService(Set<String> allowedAudiences, Function<String, PublicKey> keyResolver) {
        this.allowedAudiences = allowedAudiences;
        this.keyResolver = keyResolver;
    }

    public AppleUser verifyIdentityToken(String identityToken) {
        if (allowedAudiences.isEmpty()) {
            throw new IllegalStateException("애플 로그인이 아직 설정되지 않았습니다.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(header -> {
                        if (header instanceof JwsHeader jws && jws.getKeyId() != null) {
                            PublicKey key = keyResolver.apply(jws.getKeyId());
                            if (key != null) {
                                return key;
                            }
                        }
                        throw new JwtException("알 수 없는 서명 키입니다.");
                    })
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 애플 로그인 정보입니다.");
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || audience.stream().noneMatch(allowedAudiences::contains)) {
            throw new IllegalArgumentException("유효하지 않은 애플 로그인 정보입니다.");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 애플 로그인 정보입니다.");
        }

        String email = claims.get("email", String.class);
        // email_verified는 true/"true" 둘 다 올 수 있다
        boolean verified = "true".equals(String.valueOf(claims.get("email_verified")));
        return new AppleUser(claims.getSubject(), email, verified);
    }

    // Apple 공개키(JWKS)를 가져와 1시간 동안 보관한다. 모르는 kid가 오면 한 번 더 새로 받아본다.
    static class CachingAppleKeys {

        private final RestClient restClient;
        private Map<String, PublicKey> keys = Map.of();
        private long fetchedAt = 0;

        CachingAppleKeys(RestClient restClient) {
            this.restClient = restClient;
        }

        synchronized PublicKey find(String kid) {
            long now = System.currentTimeMillis();
            boolean stale = now - fetchedAt > KEY_CACHE.toMillis();
            if (stale || !keys.containsKey(kid)) {
                refresh(now);
            }
            return keys.get(kid);
        }

        private void refresh(long now) {
            try {
                Map<String, List<Map<String, String>>> body = restClient.get()
                        .uri(KEYS_URL)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                Map<String, PublicKey> fresh = new HashMap<>();
                KeyFactory factory = KeyFactory.getInstance("RSA");
                for (Map<String, String> jwk : body.get("keys")) {
                    BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n")));
                    BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e")));
                    fresh.put(jwk.get("kid"), factory.generatePublic(new RSAPublicKeySpec(n, e)));
                }
                keys = fresh;
                fetchedAt = now;
            } catch (RestClientException | java.security.GeneralSecurityException | NullPointerException e) {
                throw new IllegalStateException("애플 서버에서 로그인 확인 정보를 가져오지 못했습니다.");
            }
        }
    }
}
