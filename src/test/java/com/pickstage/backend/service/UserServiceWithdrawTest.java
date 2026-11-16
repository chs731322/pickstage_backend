package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.ChecklistRepository;
import com.pickstage.backend.repository.EmailVerificationRepository;
import com.pickstage.backend.repository.InterestArtistRepository;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PasswordResetTokenRepository;
import com.pickstage.backend.repository.PushTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.UserBlockRepository;
import com.pickstage.backend.repository.UserRepository;

class UserServiceWithdrawTest {

    @Test
    void 탈퇴하면_차단_기록과_푸시_기기도_지워진다() {
        UserRepository userRepository = mock(UserRepository.class);
        UserBlockRepository userBlockRepository = mock(UserBlockRepository.class);
        PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
        InterestArtistRepository interestArtistRepository = mock(InterestArtistRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserService service = new UserService(userRepository, mock(RefreshTokenRepository.class),
                mock(PasswordEncoder.class), mock(PasswordResetTokenRepository.class),
                mock(EmailVerificationRepository.class), interestArtistRepository, mock(ChecklistRepository.class),
                notificationRepository, mock(BookmarkService.class), mock(UploadedImagePolicy.class),
                new ProfanityFilter(), userBlockRepository, pushTokenRepository);
        User user = User.builder().id(5L).email("a@example.com").nickname("닉네임").role(User.Role.user).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        service.withdraw(5L);

        assertNotNull(user.getDeletedAt());
        assertEquals("탈퇴한 회원", user.getNickname());
        verify(userBlockRepository).deleteByBlockerIdOrBlockedId(5L, 5L);
        verify(notificationRepository).deleteByUserId(5L);
        verify(pushTokenRepository).deleteByUserId(5L);
    }
}
