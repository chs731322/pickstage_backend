package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    @Query("SELECT c FROM Concert c WHERE " +
            "(:region IS NULL OR c.region = :region) AND " +
            "(:genre IS NULL OR c.genre = :genre) AND " +
            "(:keyword IS NULL OR c.title LIKE %:keyword%)")
    Page<Concert> findByFilters(
            @Param("region") String region,
            @Param("genre") String genre,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 지역 필터
    Page<Concert> findByRegion(String region, Pageable pageable);

    // 장르 필터
    Page<Concert> findByGenre(String genre, Pageable pageable);

    // 지역 + 장르 필터
    Page<Concert> findByRegionAndGenre(String region, String genre, Pageable pageable);
}