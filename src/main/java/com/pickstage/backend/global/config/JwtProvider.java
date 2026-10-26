package com.pickstage.backend.global.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.secretKey =
                Keys.hmacShaKeyFor(
                        secret.getBytes(StandardCharsets.UTF_8)
                );

        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // Access Token 생성
    public String generateAccessToken(
            Long userId,
            String role) {

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TYPE_ACCESS)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + accessExpiration
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성
    public String generateRefreshToken(Long userId) {

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + refreshExpiration
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {

        return Long.parseLong(
                getClaims(token).getSubject()
        );
    }

    // 토큰에서 role 추출
    public String getRole(String token) {

        return getClaims(token)
                .get("role", String.class);
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {

        try {
            getClaims(token);
            return true;

        } catch (ExpiredJwtException e) {

            System.out.println(
                    "만료된 토큰: "
                            + e.getMessage()
            );

        } catch (JwtException e) {

            System.out.println(
                    "유효하지 않은 토큰: "
                            + e.getMessage()
            );
        }

        return false;
    }

    // 액세스 토큰인지 확인한다. 리프레시 토큰을 API 인증에 쓰지 못하게 하려는 검사다.
    // type 클레임이 생기기 전에 발급된 토큰은 role 클레임이 있으면 액세스 토큰으로 본다 (리프레시 토큰에는 role이 없다).
    public boolean isAccessToken(String token) {
        Claims claims = getClaims(token);
        String type = claims.get("type", String.class);
        if (type == null) {
            return claims.get("role") != null;
        }
        return TYPE_ACCESS.equals(type);
    }

    // 리프레시 토큰인지 확인한다 (토큰 재발급에는 리프레시 토큰만 쓸 수 있다)
    public boolean isRefreshToken(String token) {
        Claims claims = getClaims(token);
        String type = claims.get("type", String.class);
        if (type == null) {
            return claims.get("role") == null;
        }
        return TYPE_REFRESH.equals(type);
    }

    // 토큰 만료 여부 확인
    public boolean isExpired(String token) {

        try {

            getClaims(token);
            return false;

        } catch (ExpiredJwtException e) {

            return true;

        } catch (JwtException e) {

            return false;
        }
    }

    // Claims 파싱
    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}