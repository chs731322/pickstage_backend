package com.pickstage.backend.repository;

import com.pickstage.backend.entity.FestivalTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalTicketRepository extends JpaRepository<FestivalTicket, Long> {

    List<FestivalTicket> findByFestivalIdOrderBySortOrderAsc(Long festivalId);
}
