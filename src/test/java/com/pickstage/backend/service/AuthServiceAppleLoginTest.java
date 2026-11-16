package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pickstage.backend.dto.AuthResponse;
import com.pickstage.backend.entity.RefreshToken;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.global.config.JwtProvider;
import com.pickstage.backend.repository.PasswordResetTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserRepository;

// 애플 로그인: 처음이면 가입, 다시 오면 그대로 로그인, 막아야 할 경우는 막는다
class AuthServiceAppleLoginTest {

    private static final String SUB = "001234.abcdef.5678";

    private UserRepository userRepository;
    private AppleService appleService;
    private JwtProvider jwtProvider;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        appleService = mock(AppleService.class);
        jwtProvider = mock(JwtProvider.class);
        service = new AuthService(userRepository, mock(RefreshTokenRepository.class),
                mock(PasswordResetTokenRepository.class), jwtProvider, mock(PasswordEncoder.class),
                mock(EmailService.class), mock(KakaoService.class), mock(NaverService.class),
                mock(GoogleService.class), appleService, new ProfanityFilter());
        when(jwtProvider.generateAccessToken(any(), any())).thenReturn("access");
        when(jwtProvider.generateRefreshToken(any())).thenReturn("refresh");
    }

    private void tokenIs(String email) {
        when(appleService.verifyIdentityToken("token"))
                .thenReturn(new AppleService.AppleUser(SUB, email, true));
    }

    @Test
    void 처음_로그인하면_가입하고_받은_이름을_닉네임으로_쓴다() {
        tokenIs("me@privaterelay.appleid.com");
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.empty());

        AuthResponse response = service.appleLogin("token", "해솔");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(User.SocialProvider.apple, saved.getValue().getSocialProvider());
        assertEquals(SUB, saved.getValue().getSocialId());
        assertEquals("me@privaterelay.appleid.com", saved.getValue().getEmail());
        assertEquals("해솔", saved.getValue().getNickname());
        assertEquals("access", response.getAccessToken());
    }

    @Test
    void 이름이_없으면_애플사용자로_가입한다() {
        tokenIs("me@privaterelay.appleid.com");
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.empty());

        service.appleLogin("token", null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("애플사용자", saved.getValue().getNickname());
    }

    @Test
    void 닉네임이_이미_있으면_뒤에_번호를_붙인다() {
        tokenIs("me@privaterelay.appleid.com");
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("해솔")).thenReturn(true);

        service.appleLogin("token", "해솔");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("해솔5678", saved.getValue().getNickname());
    }

    @Test
    void 이미_가입한_애플_회원은_새로_만들지_않고_로그인한다() {
        tokenIs(null); // 두 번째부터 Apple은 이메일을 주지 않는다
        User existing = User.builder().id(5L).email("me@privaterelay.appleid.com")
                .socialProvider(User.SocialProvider.apple).socialId(SUB).role(User.Role.user).build();
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.of(existing));

        AuthResponse response = service.appleLogin("token", null);

        verify(userRepository, never()).save(any());
        assertEquals("access", response.getAccessToken());
    }

    @Test
    void 탈퇴한_회원은_로그인할_수_없다() {
        tokenIs(null);
        User withdrawn = User.builder().id(5L).socialProvider(User.SocialProvider.apple).socialId(SUB)
                .role(User.Role.user).deletedAt(LocalDateTime.now()).build();
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.of(withdrawn));

        assertThrows(IllegalArgumentException.class, () -> service.appleLogin("token", null));
    }

    @Test
    void 정지된_회원은_로그인할_수_없다() {
        tokenIs(null);
        User banned = User.builder().id(5L).socialProvider(User.SocialProvider.apple).socialId(SUB)
                .role(User.Role.user).bannedAt(LocalDateTime.now()).build();
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.of(banned));

        assertThrows(IllegalArgumentException.class, () -> service.appleLogin("token", null));
    }

    @Test
    void 처음인데_이메일이_없으면_가입하지_않는다() {
        tokenIs(null);
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.appleLogin("token", "해솔"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void 같은_이메일로_이미_가입한_사람이_있으면_막는다() {
        tokenIs("taken@example.com");
        when(userRepository.findBySocialProviderAndSocialId(User.SocialProvider.apple, SUB)).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.appleLogin("token", "해솔"));
        verify(userRepository, never()).save(any());
    }
}
