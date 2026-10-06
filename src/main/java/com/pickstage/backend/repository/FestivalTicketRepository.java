package com.pickstage.backend.repository;

import com.pickstage.backend.entity.FestivalTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FestivalTicketRepository extends JpaRepository<FestivalTicket, Long> {

    List<FestivalTicket> findByFestivalIdOrderBySortOrderAsc(Long festivalId);

    // 여러 공연의 티켓을 한 번에 조회 (목록에서 첫 티켓 이름을 붙일 때 N+1 방지)
    List<FestivalTicket> findByFestivalIdInOrderBySortOrderAsc(Collection<Long> festivalIds);

    void deleteByFestivalId(Long festivalId);
}
