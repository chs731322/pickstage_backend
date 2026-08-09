package com.pickstage.backend.repository;

import org.springframework.stereotype.Repository;
import com.pickstage.backend.entity.Post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE " +
            "(:category IS NULL OR p.category = :category) AND " +
            "(:festivalId IS NULL OR p.festivalId = :festivalId) AND " +
            "p.deletedAt IS NULL")
    Page<Post> findByFilters(Post.Category category, Long festivalId, Pageable pageable);

}
