package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pickstage.backend.dto.SigninRequest;
import com.pickstage.backend.entity.RefreshToken;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.global.config.JwtProvider;
import com.pickstage.backend.repository.PasswordResetTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserRepository;

// 이용이 정지된 계정은 로그인하거나 토큰을 다시 받을 수 없다
class AuthServiceBanTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtProvider = mock(JwtProvider.class);
        service = new AuthService(userRepository, mock(RefreshTokenRepository.class),
                mock(PasswordResetTokenRepository.class), jwtProvider, passwordEncoder, mock(EmailService.class),
                mock(KakaoService.class), mock(NaverService.class), mock(GoogleService.class), new ProfanityFilter());
    }

    private SigninRequest request(String password) {
        // SigninRequest에는 setter가 없어서 필드를 직접 채운다
        SigninRequest r = new SigninRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(r, "email", "banned@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(r, "password", password);
        return r;
    }

    private User bannedUser() {
        return User.builder().id(7L).email("banned@example.com").passwordHash("hash").role(User.Role.user)
                .bannedAt(LocalDateTime.now()).build();
    }

    @Test
    void 정지된_계정은_비밀번호가_맞아도_로그인할_수_없다() {
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(bannedUser()));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.signin(request("right")));

        assertEquals(AuthService.BANNED_MESSAGE, e.getMessage());
    }

    @Test
    void 비밀번호가_틀리면_정지_여부를_알려주지_않는다() {
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(bannedUser()));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.signin(request("wrong")));

        assertEquals("이메일 또는 비밀번호가 일치하지 않습니다.", e.getMessage());
    }

    @Test
    void 정지된_계정은_토큰을_다시_받을_수_없다() {
        when(jwtProvider.isExpired("refresh")).thenReturn(false);
        when(jwtProvider.validateToken("refresh")).thenReturn(true);
        when(jwtProvider.isRefreshToken("refresh")).thenReturn(true);
        when(jwtProvider.getUserId("refresh")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(bannedUser()));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.reissue("refresh"));

        assertEquals("유효하지 않은 토큰입니다.", e.getMessage());
    }
}
