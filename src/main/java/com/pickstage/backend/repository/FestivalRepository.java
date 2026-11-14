package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Festival;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FestivalRepository extends JpaRepository<Festival, Long> {

        @Query("SELECT f FROM Festival f WHERE " +
                        "(:region IS NULL OR f.region = :region) AND " +
                        "(:useGenres = false OR f.genre IN :genres) AND " +
                        "(:keyword IS NULL OR f.title LIKE %:keyword% OR f.id IN (" +
                        "SELECT fa.festival.id FROM FestivalArtist fa " +
                        "WHERE fa.artist.name LIKE %:keyword% OR fa.artist.englishName LIKE %:keyword%)) AND " +
                        "(:openAfter IS NULL OR f.ticketOpenDate > :openAfter) AND " +
                        "(:openBefore IS NULL OR f.ticketOpenDate <= :openBefore) AND " +
                        "(:endAfter IS NULL OR f.endDate >= :endAfter) AND " +
                        "(:dateFrom IS NULL OR f.endDate >= :dateFrom) AND " +
                        "(:dateTo IS NULL OR f.startDate <= :dateTo) AND " +
                        "(:outdoor = false OR (f.venueName LIKE '%공원%' OR f.venueName LIKE '%해변%' OR f.venueName LIKE '%해수욕장%' OR f.venueName LIKE '%광장%' OR f.venueName LIKE '%섬%' OR f.venueName LIKE '%잔디%' OR f.venueName LIKE '%운동장%' OR f.venueName LIKE '%경기장%' OR f.venueName LIKE '%평화누리%' OR f.venueName LIKE '%마당%' OR f.venueName LIKE '%야외%' OR f.venueName LIKE '%캠핑%'))")
        Page<Festival> findByFilters(
                        @Param("region") String region,
                        @Param("useGenres") boolean useGenres,
                        @Param("genres") java.util.Collection<String> genres,
                        @Param("keyword") String keyword,
                        @Param("openAfter") LocalDateTime openAfter,
                        @Param("openBefore") LocalDateTime openBefore,
                        @Param("endAfter") LocalDate endAfter,
                        @Param("dateFrom") LocalDate dateFrom,
                        @Param("dateTo") LocalDate dateTo,
                        @Param("outdoor") boolean outdoor,
                        Pageable pageable);

        // 공연 당일 알림용: 오늘 시작하는 공연
        List<Festival> findByStartDate(LocalDate startDate);

        // 해당 공연장에서 예정된 페스티벌
        List<Festival> findByVenueIdAndStartDateGreaterThanEqual(Long venueId, LocalDate today);

        // 관리자 공연 등록 시 제목 중복 확인
        boolean existsByTitle(String title);

        @Query("SELECT f FROM Festival f WHERE f.startDate <= :endOfMonth AND f.endDate >= :startOfMonth")
        List<Festival> findByDateRange(@Param("startOfMonth") LocalDate startOfMonth,
                        @Param("endOfMonth") LocalDate endOfMonth);
}