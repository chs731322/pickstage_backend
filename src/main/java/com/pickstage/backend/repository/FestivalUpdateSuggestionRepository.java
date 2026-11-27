package com.pickstage.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.FestivalUpdateSuggestion;

public interface FestivalUpdateSuggestionRepository extends JpaRepository<FestivalUpdateSuggestion, Long> {

    List<FestivalUpdateSuggestion> findByStatusOrderByCreatedAtDesc(FestivalUpdateSuggestion.Status status);

    List<FestivalUpdateSuggestion> findByFestivalIdAndStatusIn(Long festivalId, Collection<FestivalUpdateSuggestion.Status> statuses);
}
