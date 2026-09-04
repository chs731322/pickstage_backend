package com.pickstage.backend.repository;

import org.springframework.stereotype.Repository;
import com.pickstage.backend.entity.FAQ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Repository
public interface FAQRepository extends JpaRepository<FAQ, Long> {
    // FAQ 목록 조회 (정렬 순서 기준)
    @Query("SELECT f FROM FAQ f ORDER BY f.sortOrder ASC")
    List<FAQ> findAllByOrderBySortOrderAsc();
}
