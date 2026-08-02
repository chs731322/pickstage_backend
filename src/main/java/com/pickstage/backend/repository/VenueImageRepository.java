package com.pickstage.backend.repository;

import com.pickstage.backend.entity.VenueImage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface VenueImageRepository extends JpaRepository<VenueImage, Long> {
    List<VenueImage> findByVenueIdOrderBySortOrderAsc(Long venueId);
}