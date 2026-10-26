package com.pickstage.backend.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtProviderTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private final JwtProvider provider = new JwtProvider(SECRET, 1_800_000L, 1_209_600_000L);

    @Test
    void 액세스_토큰은_액세스로만_인정된다() {
        String token = provider.generateAccessToken(7L, "user");
        assertTrue(provider.isAccessToken(token));
        assertFalse(provider.isRefreshToken(token));
        assertEquals(7L, provider.getUserId(token));
    }

    @Test
    void 리프레시_토큰은_API_인증에_쓸_수_없다() {
        String token = provider.generateRefreshToken(7L);
        assertTrue(provider.isRefreshToken(token));
        assertFalse(provider.isAccessToken(token));
    }

    @Test
    void type이_없던_이전_토큰은_role_유무로_구분한다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date exp = new Date(System.currentTimeMillis() + 60_000);

        String legacyAccess = Jwts.builder().subject("7").claim("role", "user").expiration(exp).signWith(key).compact();
        String legacyRefresh = Jwts.builder().subject("7").expiration(exp).signWith(key).compact();

        assertTrue(provider.isAccessToken(legacyAccess));
        assertFalse(provider.isRefreshToken(legacyAccess));
        assertFalse(provider.isAccessToken(legacyRefresh));
        assertTrue(provider.isRefreshToken(legacyRefresh));
    }
}
