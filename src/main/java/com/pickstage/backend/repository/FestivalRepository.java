package com.pickstage.backend.repository;

import com.pickstage.backend.entity.Festival;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
