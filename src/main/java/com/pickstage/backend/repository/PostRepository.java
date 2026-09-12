package com.pickstage.backend.repository;

import org.springframework.stereotype.Repository;
import com.pickstage.backend.entity.Post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE " +
            "(:category IS NULL OR p.category = :category) AND " +
            "(:festivalId IS NULL OR p.festivalId = :festivalId) AND " +
            "p.deletedAt IS NULL")
    Page<Post> findByFilters(Post.Category category, Long festivalId, Pageable pageable);

    // 공연 후기 하이라이트 집계용 - 특정 공연의 후기 카테고리 게시글 전체 조회
    List<Post> findByCategoryAndFestivalIdAndDeletedAtIsNull(Post.Category category, Long festivalId);

    // 관리자 공연 삭제 시, 그 공연에 딸린 게시글은 지우지 않고 연결만 해제
    @Modifying
    @Query("UPDATE Post p SET p.festivalId = NULL WHERE p.festivalId = :festivalId")
    void detachFestivalId(Long festivalId);

}
