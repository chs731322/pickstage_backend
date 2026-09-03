package com.pickstage.backend.repository;

import com.pickstage.backend.entity.FestivalImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalImageRepository extends JpaRepository<FestivalImage, Long> {

    List<FestivalImage> findByFestivalIdOrderBySortOrderAsc(Long festivalId);

    void deleteByFestivalId(Long festivalId);
}
