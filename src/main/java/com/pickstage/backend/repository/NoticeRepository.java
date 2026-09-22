package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 공지사항 목록 조회 (고정 공지 먼저, 그 안에서 최신순, 제목/내용 키워드 검색 지원)
    @Query("SELECT n FROM Notice n WHERE " +
            "(:keyword IS NULL OR n.title LIKE %:keyword% OR n.content LIKE %:keyword%) " +
            "ORDER BY n.isPinned DESC, n.createdAt DESC")
    Page<Notice> findByFilters(@Param("keyword") String keyword, Pageable pageable);
}
