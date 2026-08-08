package com.pickstage.backend.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pickstage.backend.entity.Bookmark;
import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    
    // 이미 북마크가 존재하는지 확인하는 메서드
    boolean existsByUserIdAndFestivalId(Long userId, Long festivalId);

    // 북마크 추가 메서드
    Bookmark save(Bookmark bookmark);

    // 북마크 삭제 메서드
    void deleteByUserIdAndFestivalId(Long userId, Long festivalId);

    // 특정 사용자의 모든 북마크를 조회하는 메서드
    List<Bookmark> findByUserId(Long userId);
}
