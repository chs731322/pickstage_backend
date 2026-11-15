package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.BlockedUserResponse;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.UserBlock;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.UserBlockRepository;
import com.pickstage.backend.repository.UserRepository;

@Service
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public BlockService(UserBlockRepository userBlockRepository, UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
    }

    // 이 사람이 차단한 사용자 id들 (로그인하지 않았으면 빈 집합)
    @Transactional(readOnly = true)
    public Set<Long> getBlockedIds(Long blockerId) {
        if (blockerId == null) {
            return Set.of();
        }
        return new HashSet<>(userBlockRepository.findBlockedIdsByBlockerId(blockerId));
    }

    // blockerId가 targetUserId를 차단했는지
    @Transactional(readOnly = true)
    public boolean isBlocked(Long blockerId, Long targetUserId) {
        if (blockerId == null || targetUserId == null) {
            return false;
        }
        return userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId);
    }

    // 사용자 차단
    @Transactional
    public void block(Long blockerId, Long targetUserId) {
        if (blockerId.equals(targetUserId)) {
            throw new IllegalArgumentException("본인을 차단할 수 없습니다.");
        }
        userRepository.findById(targetUserId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));

        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId)) {
            throw new DuplicateRequestException("이미 차단한 사용자입니다.");
        }

        userBlockRepository.save(UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(targetUserId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // 차단 해제
    @Transactional
    public void unblock(Long blockerId, Long targetUserId) {
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, targetUserId)) {
            throw new IllegalStateException("차단하지 않은 사용자입니다.");
        }
        userBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetUserId);
    }

    // 내가 차단한 사용자 목록 (탈퇴한 사용자는 이름 대신 "탈퇴한 회원"으로 보인다)
    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        List<UserBlock> blocks = userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        Map<Long, User> users = userRepository
                .findAllById(blocks.stream().map(UserBlock::getBlockedId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return blocks.stream()
                .map(b -> {
                    User u = users.get(b.getBlockedId());
                    return BlockedUserResponse.builder()
                            .userId(b.getBlockedId())
                            .nickname(u != null ? u.getNickname() : "탈퇴한 회원")
                            .profileImage(u != null ? u.getProfileImage() : null)
                            .blockedAt(b.getCreatedAt())
                            .build();
                })
                .toList();
    }
}
