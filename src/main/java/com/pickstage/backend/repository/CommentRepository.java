package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long postId);

    List<Comment> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long parentId);

    // 목록에 보여줄 댓글 수 (삭제된 댓글 제외)
    long countByPostIdAndDeletedAtIsNull(Long postId);
}