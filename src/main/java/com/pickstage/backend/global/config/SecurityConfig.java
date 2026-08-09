package com.pickstage.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtProvider jwtProvider;
	private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

	public SecurityConfig(JwtProvider jwtProvider, CustomAuthenticationEntryPoint customAuthenticationEntryPoint) {
		this.jwtProvider = jwtProvider;
		this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.cors(cors -> {})
				// CSRF 비활성화 (REST API라 불필요)
				.csrf(csrf -> csrf.disable())

				// 세션 사용 안 함 (JWT 방식이라 STATELESS)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				// 인증 실패 시 응답 형식 커스터마이징
				.exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))

				// URL별 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						// 인증 없이 접근 가능한 URL
						.requestMatchers("/api/auth/**", "/api/users/check-nickname").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/festivals/**", "/api/venues/**", "/api/artists/**",
								"/api/posts/**")
						.permitAll()
						// 나머지는 로그인 필요 (관심 아티스트 추가/삭제 등 POST/DELETE 포함)
						.anyRequest().authenticated())

				// JWT 필터 등록
				.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	// 비밀번호 암호화
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}