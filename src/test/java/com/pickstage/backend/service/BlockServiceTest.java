package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pickstage.backend.dto.BlockedUserResponse;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.UserBlock;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.UserBlockRepository;
import com.pickstage.backend.repository.UserRepository;

class BlockServiceTest {

    private UserBlockRepository userBlockRepository;
    private UserRepository userRepository;
    private BlockService service;

    @BeforeEach
    void setUp() {
        userBlockRepository = mock(UserBlockRepository.class);
        userRepository = mock(UserRepository.class);
        service = new BlockService(userBlockRepository, userRepository);
    }

    @Test
    void 다른_사용자를_차단하면_저장된다() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).nickname("상대").build()));

        service.block(1L, 2L);

        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    void 나_자신은_차단할_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> service.block(1L, 1L));
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void 없거나_탈퇴한_사용자는_차단할_수_없다() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(userRepository.findById(3L)).thenReturn(Optional.of(User.builder().id(3L).deletedAt(LocalDateTime.now()).build()));

        assertThrows(IllegalStateException.class, () -> service.block(1L, 2L));
        assertThrows(IllegalStateException.class, () -> service.block(1L, 3L));
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void 이미_차단한_사용자는_다시_차단할_수_없다() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThrows(DuplicateRequestException.class, () -> service.block(1L, 2L));
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void 차단을_해제한다() {
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        service.unblock(1L, 2L);

        verify(userBlockRepository).deleteByBlockerIdAndBlockedId(1L, 2L);
    }

    @Test
    void 차단하지_않은_사용자는_해제할_수_없다() {
        assertThrows(IllegalStateException.class, () -> service.unblock(1L, 2L));
        verify(userBlockRepository, never()).deleteByBlockerIdAndBlockedId(any(), any());
    }

    @Test
    void 차단_목록은_최근_순으로_보이고_탈퇴한_회원은_이름이_가려진다() {
        LocalDateTime now = LocalDateTime.now();
        when(userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                UserBlock.builder().blockerId(1L).blockedId(3L).createdAt(now).build(),
                UserBlock.builder().blockerId(1L).blockedId(2L).createdAt(now.minusDays(1)).build()));
        // 3번 사용자는 DB에서 찾을 수 없는 경우
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().id(2L).nickname("상대").profileImage("p.png").build()));

        List<BlockedUserResponse> result = service.getBlockedUsers(1L);

        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).getUserId());
        assertEquals("탈퇴한 회원", result.get(0).getNickname());
        assertNull(result.get(0).getProfileImage());
        assertEquals("상대", result.get(1).getNickname());
        assertEquals("p.png", result.get(1).getProfileImage());
    }

    @Test
    void 차단한_사용자_id_집합을_돌려주고_로그인하지_않았으면_빈_집합이다() {
        when(userBlockRepository.findBlockedIdsByBlockerId(1L)).thenReturn(List.of(2L, 3L));

        assertEquals(java.util.Set.of(2L, 3L), service.getBlockedIds(1L));
        assertEquals(java.util.Set.of(), service.getBlockedIds(null));
        verify(userBlockRepository, never()).findBlockedIdsByBlockerId(null);
    }

    @Test
    void 차단_여부를_확인한다() {
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(service.isBlocked(1L, 2L));
        org.junit.jupiter.api.Assertions.assertFalse(service.isBlocked(1L, 3L));
        org.junit.jupiter.api.Assertions.assertFalse(service.isBlocked(null, 2L));
    }
}
