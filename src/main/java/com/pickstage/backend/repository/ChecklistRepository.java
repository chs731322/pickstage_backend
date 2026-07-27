package com.pickstage.backend.repository;

import java.util.List;
import com.pickstage.backend.entity.Checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChecklistRepository extends JpaRepository<Checklist, Long> {
    List<Checklist> findByUserIdOrderBySortOrderAsc(Long userId);
    boolean existsByUserId(Long userId);
    void deleteByUserId(Long userId);

}
