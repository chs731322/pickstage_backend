package com.pickstage.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pickstage.backend.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    // 이미 좋아요했는지 확인
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    // 좋아요 취소
    void deleteByPostIdAndUserId(Long postId, Long userId);
}
