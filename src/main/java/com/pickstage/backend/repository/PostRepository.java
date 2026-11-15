package com.pickstage.backend.repository;

import org.springframework.stereotype.Repository;
import com.pickstage.backend.entity.Post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE " +
            "(:category IS NULL OR p.category = :category) AND " +
            "(:festivalId IS NULL OR p.festivalId = :festivalId) AND " +
            "p.deletedAt IS NULL AND p.hiddenAt IS NULL AND " +
            "p.userId NOT IN :excludedUserIds")
    Page<Post> findByFilters(Post.Category category, Long festivalId, Collection<Long> excludedUserIds, Pageable pageable);

    // 마이페이지 "내가 쓴 글": 내가 쓴 삭제되지 않은 글 (카테고리로 거를 수 있다)
    @Query("SELECT p FROM Post p WHERE p.userId = :userId AND " +
            "(:category IS NULL OR p.category = :category) AND " +
            "p.deletedAt IS NULL AND p.hiddenAt IS NULL")
    Page<Post> findMine(Long userId, Post.Category category, Pageable pageable);

    // 공연 후기 하이라이트 집계용 - 특정 공연의 후기 카테고리 게시글 전체 조회
    List<Post> findByCategoryAndFestivalIdAndDeletedAtIsNullAndHiddenAtIsNull(Post.Category category, Long festivalId);

    // 관리자 공연 삭제 시, 그 공연에 딸린 게시글은 지우지 않고 연결만 해제
    @Modifying
    @Query("UPDATE Post p SET p.festivalId = NULL WHERE p.festivalId = :festivalId")
    void detachFestivalId(Long festivalId);

}
