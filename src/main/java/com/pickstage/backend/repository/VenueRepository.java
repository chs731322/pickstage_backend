package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    // 공연장 목록 조회 (최신순 - Pageable의 Sort로 정렬)
    @Query("SELECT v FROM Venue v WHERE " +
            "(:region IS NULL OR v.region = :region) AND " +
            "(:keyword IS NULL OR v.name LIKE %:keyword%)")
    Page<Venue> findByFilters(
            @Param("region") String region,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 공연장 목록 조회 (후기 개수 기준 인기순)
    @Query("SELECT v FROM Venue v LEFT JOIN VenueReview vr " +
            "ON vr.venueId = v.id AND vr.deletedAt IS NULL WHERE " +
            "(:region IS NULL OR v.region = :region) AND " +
            "(:keyword IS NULL OR v.name LIKE %:keyword%) " +
            "GROUP BY v.id ORDER BY COUNT(vr.id) DESC")
    Page<Venue> findByFiltersOrderByReviewCountDesc(
            @Param("region") String region,
            @Param("keyword") String keyword,
            Pageable pageable);
}
