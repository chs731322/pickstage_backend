package com.pickstage.backend.service;

import org.springframework.stereotype.Service;

import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final FestivalService festivalService;
    private final FestivalRepository festivalRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository, FestivalService festivalService, FestivalRepository festivalRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.festivalService = festivalService;
        this.festivalRepository = festivalRepository;
    }

    // 북마크 조회
    public boolean isBookmarked(Long userId, Long festivalId) {
        return bookmarkRepository.existsByUserIdAndFestivalId(userId, festivalId);
    }

    // 북마크 추가
    public void addBookmark(Long userId, Long festivalId) {
        if (!festivalRepository.existsById(festivalId)) {
            throw new IllegalStateException("존재하지 않는 공연입니다.");
        }

        // 이미 북마크가 존재하는지 확인
        if (bookmarkRepository.existsByUserIdAndFestivalId(userId, festivalId)) {
            throw new org.springframework.dao.DataIntegrityViolationException("이미 북마크한 공연입니다.");
        }

        // 북마크 추가
        bookmarkRepository.save(
                new com.pickstage.backend.entity.Bookmark(null, userId, festivalId, java.time.LocalDateTime.now()));

        // 축제의 북마크 수 증가
        festivalService.incrementBookmarkCount(festivalId);
    }

    // 북마크 삭제
    @Transactional
    public void removeBookmark(Long userId, Long festivalId) {
        // 북마크가 존재하는지 확인
        if (!bookmarkRepository.existsByUserIdAndFestivalId(userId, festivalId)) {
            throw new IllegalStateException("북마크가 존재하지 않습니다.");
        }

        // 북마크 삭제
        bookmarkRepository.deleteByUserIdAndFestivalId(userId, festivalId);

        // 북마크 수 감소
        festivalService.decrementBookmarkCount(festivalId);
    }

}
