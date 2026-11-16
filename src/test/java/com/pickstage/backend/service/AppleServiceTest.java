package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;

// Apple identity token 검증: 서명, 발급자, 앱(audience), 만료를 확인한다
class AppleServiceTest {

    private static final String BUNDLE_ID = "com.pickstage.app";

    private KeyPair appleKey;
    private KeyPair otherKey;
    private AppleService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        appleKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
        // kid가 "kid1"인 키만 Apple이 가진 것으로 흉내낸다
        service = new AppleService(Set.of(BUNDLE_ID), kid -> "kid1".equals(kid) ? appleKey.getPublic() : null);
    }

    private String token(KeyPair signer, String kid, String issuer, String audience, long expiresInMs) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("001234.abcdef.5678")
                .claim("email", "user@privaterelay.appleid.com")
                .claim("email_verified", "true")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(signer.getPrivate())
                .compact();
    }

    @Test
    void 올바른_토큰은_계정_정보를_돌려준다() {
        AppleService.AppleUser user = service.verifyIdentityToken(
                token(appleKey, "kid1", AppleService.ISSUER, BUNDLE_ID, 60_000));

        assertEquals("001234.abcdef.5678", user.subject());
        assertEquals("user@privaterelay.appleid.com", user.email());
        assertTrue(user.emailVerified());
    }

    @Test
    void 다른_키로_서명한_토큰은_거절한다() {
        String forged = token(otherKey, "kid1", AppleService.ISSUER, BUNDLE_ID, 60_000);
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken(forged));
    }

    @Test
    void 모르는_kid의_토큰은_거절한다() {
        String t = token(appleKey, "unknown", AppleService.ISSUER, BUNDLE_ID, 60_000);
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken(t));
    }

    @Test
    void Apple이_발급하지_않은_토큰은_거절한다() {
        String t = token(appleKey, "kid1", "https://evil.example.com", BUNDLE_ID, 60_000);
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken(t));
    }

    @Test
    void 다른_앱용_토큰은_거절한다() {
        String t = token(appleKey, "kid1", AppleService.ISSUER, "com.other.app", 60_000);
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken(t));
    }

    @Test
    void 만료된_토큰은_거절한다() {
        String t = token(appleKey, "kid1", AppleService.ISSUER, BUNDLE_ID, -60_000);
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken(t));
    }

    @Test
    void 토큰이_아닌_문자열은_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> service.verifyIdentityToken("not-a-jwt"));
    }

    @Test
    void 허용할_앱이_설정되지_않았으면_애플_로그인을_받지_않는다() {
        AppleService unconfigured = new AppleService(Set.of(), kid -> appleKey.getPublic());
        String t = token(appleKey, "kid1", AppleService.ISSUER, BUNDLE_ID, 60_000);
        assertThrows(IllegalStateException.class, () -> unconfigured.verifyIdentityToken(t));
    }
}
