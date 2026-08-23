package com.pickstage.backend.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pickstage.backend.entity.VenueReview;
import java.util.List;

@Repository
public interface VenueReviewRepository extends JpaRepository<VenueReview, Long> {
    List<VenueReview> findByVenueIdAndDeletedAtIsNull(Long venueId);
}
