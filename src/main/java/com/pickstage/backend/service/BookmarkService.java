package com.pickstage.backend.service;

import org.springframework.stereotype.Service;

import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalRepository;
import org.springframework.transaction.annotation.Transactional;
import com.pickstage.backend.entity.Bookmark;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.dto.FestivalListResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final FestivalService festivalService;
    private final FestivalRepository festivalRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository, FestivalService festivalService,
            FestivalRepository festivalRepository) {
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
            throw new org.springframework.dao.DataIntegrityViolationException("북마크하지 않은 공연입니다.");
        }

        // 북마크 삭제
        bookmarkRepository.deleteByUserIdAndFestivalId(userId, festivalId);

        // 북마크 수 감소
        festivalService.decrementBookmarkCount(festivalId);
    }

    // 특정 사용자의 북마크 조회
    // bookmarkRepository.findByUserId(userId)로 북마크 목록 가져오기
    // 각 북마크의 festivalId로 festivalRepository.findById(...) 조회해서
    // FestivalListResponse로 변환 (필드: id, title, posterImage, startDate, endDate,
    // region, genre, isFestival, venueName, bookmarkCount — 이미 다 있는 필드라 Festival
    // 엔티티에서 그대로 매핑하면 됨)
    // .stream().map(...).toList()로 리스트 반환
    public List<FestivalListResponse> getUserBookmarks(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(userId);

        List<FestivalListResponse> result = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            Optional<Festival> festivalOpt = festivalRepository.findById(bookmark.getFestivalId());
            if (festivalOpt.isEmpty()) {
                continue; // 페스티벌이 존재하지 않으면 건너뜀
            }

            Festival festival = festivalOpt.get();

            FestivalListResponse dto = FestivalListResponse.builder()
                    .id(festival.getId())
                    .title(festival.getTitle())
                    .posterImage(festival.getPosterUrl())
                    .startDate(festival.getStartDate())
                    .endDate(festival.getEndDate())
                    .region(festival.getRegion())
                    .genre(festival.getGenre())
                    .isFestival(true)
                    .venueName(festival.getVenueName())
                    .bookmarkCount(festival.getBookmarkCount())
                    .build();
            result.add(dto);
        }
        return result;
    }
}
