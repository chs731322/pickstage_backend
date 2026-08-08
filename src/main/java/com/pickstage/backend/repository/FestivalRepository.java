package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Festival;

import java.time.LocalDate;

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
                        "(:genre IS NULL OR f.genre = :genre) AND " +
                        "(:keyword IS NULL OR f.title LIKE %:keyword%)")
        Page<Festival> findByFilters(
                        @Param("region") String region,
                        @Param("genre") String genre,
                        @Param("keyword") String keyword,
                        Pageable pageable);

        // 해당 공연장에서 예정된 페스티벌
        List<Festival> findByVenueIdAndStartDateGreaterThanEqual(Long venueId, LocalDate today);

        @Query("SELECT f FROM Festival f WHERE f.startDate <= :endOfMonth AND f.endDate >= :startOfMonth")
        List<Festival> findByDateRange(@Param("startOfMonth") LocalDate startOfMonth,
                        @Param("endOfMonth") LocalDate endOfMonth);
}