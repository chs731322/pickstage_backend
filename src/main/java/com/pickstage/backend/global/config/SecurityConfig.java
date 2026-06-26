package com.pickstage.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

	public SecurityConfig(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.cors(cors -> {})
				// CSRF 비활성화 (REST API라 불필요)
				.csrf(csrf -> csrf.disable())

				// 세션 사용 안 함 (JWT 방식이라 STATELESS)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				// URL별 접근 권한 설정
				.authorizeHttpRequests(auth -> auth
						// 인증 없이 접근 가능한 URL
						.requestMatchers("/api/auth/**", "/api/concerts/**", "/api/venues/**", "/api/artists/**")
						.permitAll()
						// 나머지는 로그인 필요
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