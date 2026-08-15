package com.pickstage.backend.global.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.ObjectMapper;
import com.pickstage.backend.global.response.ApiResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtProvider jwtProvider,
            ObjectMapper objectMapper) {
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> {})
                // CSRF 비활성화 (REST API라 불필요)
                .csrf(csrf -> csrf.disable())

                // 세션 사용 안 함 (JWT 방식이라 STATELESS)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 인증 실패 처리
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                authenticationEntryPoint()
                        )
                )

                // URL별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 URL
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/users/check-nickname",
                                "/api/festivals/**",
                                "/api/venues/**",
                                "/api/artists/**"
                        )
                        .permitAll()

                        // 나머지는 로그인 필요
                        .anyRequest()
                        .authenticated()
                )

                // JWT 필터 등록
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // 인증되지 않은 사용자의 접근 처리
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPoint() {

            @Override
            public void commence(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    AuthenticationException authException)
                    throws IOException, ServletException {

                response.setStatus(
                        HttpStatus.UNAUTHORIZED.value()
                );

                response.setContentType(
                        MediaType.APPLICATION_JSON_VALUE
                );

                response.setCharacterEncoding("UTF-8");

                ApiResponse<Void> body =
                        ApiResponse.fail(
                                "로그인이 필요합니다."
                        );

                objectMapper.writeValue(
                        response.getWriter(),
                        body
                );
            }
        };
    }

    // 비밀번호 암호화
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}