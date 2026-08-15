package com.pickstage.backend.service;

import com.pickstage.backend.dto.MyPageResponse;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 내 정보 조회
    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        return MyPageResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}