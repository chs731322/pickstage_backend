package com.pickstage.backend.repository;

import com.pickstage.backend.entity.FestivalArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FestivalArtistRepository extends JpaRepository<FestivalArtist, Long> {

    // 특정 공연(festival)에 출연하는 아티스트 목록
    List<FestivalArtist> findByFestivalId(Long festivalId);

    // 특정 아티스트의 예정된 공연 목록 (시작일이 오늘 이후인 것만)
    @Query("SELECT fa FROM FestivalArtist fa " +
            "WHERE fa.artist.id = :artistId AND fa.festival.startDate >= :today " +
            "ORDER BY fa.festival.startDate ASC")
    List<FestivalArtist> findUpcomingByArtistId(
            @Param("artistId") Long artistId,
            @Param("today") LocalDate today);
}
