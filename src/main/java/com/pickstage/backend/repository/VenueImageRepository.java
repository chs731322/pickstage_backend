package com.pickstage.backend.repository;

import com.pickstage.backend.entity.VenueImage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface VenueImageRepository extends JpaRepository<VenueImage, Long> {
    List<VenueImage> findByVenueIdOrderBySortOrderAsc(Long venueId);

    // 목록용 대표 이미지 (sort_order가 가장 앞선 것 하나)
    Optional<VenueImage> findFirstByVenueIdOrderBySortOrderAsc(Long venueId);
}