package com.pickstage.backend.repository;

import com.pickstage.backend.entity.FestivalArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FestivalArtistRepository extends JpaRepository<FestivalArtist, Long> {

        // 사이트맵용: 출연 공연이 하나라도 있는 아티스트 id (공연 정보가 없는 아티스트 페이지는 내용이 빈약해 제외)
        @Query("SELECT DISTINCT fa.artist.id FROM FestivalArtist fa ORDER BY fa.artist.id")
        List<Long> findLinkedArtistIds();

        // 관리자 아티스트 목록: 아티스트별 출연 공연 수
        @Query("SELECT fa.artist.id, COUNT(fa) FROM FestivalArtist fa WHERE fa.artist.id IN :artistIds GROUP BY fa.artist.id")
        List<Object[]> countByArtistIds(@Param("artistIds") List<Long> artistIds);

        @Query("SELECT COUNT(fa) FROM FestivalArtist fa WHERE fa.artist.id = :artistId")
        long countByArtistId(@Param("artistId") Long artistId);

        // 분리하기: 이 아티스트의 출연 목록(공연과 함께)과, 특정 공연에 이미 출연 중인지 확인
        @Query("SELECT fa FROM FestivalArtist fa JOIN FETCH fa.festival WHERE fa.artist.id = :artistId")
        List<FestivalArtist> findByArtistIdWithFestival(@Param("artistId") Long artistId);

        @Query("SELECT COUNT(fa) > 0 FROM FestivalArtist fa WHERE fa.festival.id = :festivalId AND fa.artist.id = :artistId")
        boolean existsLink(@Param("festivalId") Long festivalId, @Param("artistId") Long artistId);

        @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("DELETE FROM FestivalArtist fa WHERE fa.artist.id = :artistId")
        void deleteByArtistId(@Param("artistId") Long artistId);

        // 합치기: 같은 공연에 이미 합칠 대상이 출연 중이면 이쪽 행은 지우고, 나머지는 대상으로 옮긴다
        @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = "DELETE FROM festival_artists WHERE artist_id = :source AND festival_id IN "
                        + "(SELECT festival_id FROM (SELECT festival_id FROM festival_artists WHERE artist_id = :target) t)",
                        nativeQuery = true)
        void deleteDuplicatesBeforeMerge(@Param("source") Long source, @Param("target") Long target);

        @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = "UPDATE festival_artists SET artist_id = :target WHERE artist_id = :source", nativeQuery = true)
        void moveToArtist(@Param("source") Long source, @Param("target") Long target);

        // 공연 수정 시 라인업 교체용
        @org.springframework.data.jpa.repository.Modifying
        @Query("DELETE FROM FestivalArtist fa WHERE fa.festival.id = :festivalId")
        void deleteByFestivalId(@Param("festivalId") Long festivalId);

        // 특정 공연(festival)에 출연하는 아티스트 목록
        // artist를 JOIN FETCH해서 상세 조회 시 아티스트 수만큼 추가 쿼리(N+1)가 나가는 걸 방지
        @Query("SELECT fa FROM FestivalArtist fa JOIN FETCH fa.artist WHERE fa.festival.id = :festivalId")
        List<FestivalArtist> findByFestivalId(@Param("festivalId") Long festivalId);

        // 특정 아티스트의 예정·진행 중인 공연 목록 (종료일이 오늘 이후인 것)
        // festival을 JOIN FETCH: 서비스에 트랜잭션이 없어(open-in-view=false) 지연 로딩 시 LazyInitializationException 방지
        @Query("SELECT fa FROM FestivalArtist fa JOIN FETCH fa.festival f " +
                        "WHERE fa.artist.id = :artistId AND f.endDate >= :today " +
                        "ORDER BY f.startDate ASC")
        List<FestivalArtist> findUpcomingByArtistId(
                        @Param("artistId") Long artistId,
                        @Param("today") LocalDate today);

        // 특정 아티스트가 출연했던(이미 끝난) 공연 목록: 최근 공연이 먼저
        @Query("SELECT fa FROM FestivalArtist fa JOIN FETCH fa.festival f " +
                        "WHERE fa.artist.id = :artistId AND f.endDate < :today " +
                        "ORDER BY f.startDate DESC")
        List<FestivalArtist> findPastByArtistId(
                        @Param("artistId") Long artistId,
                        @Param("today") LocalDate today);

        @Query("SELECT fa FROM FestivalArtist fa JOIN FETCH fa.festival f JOIN FETCH fa.artist " +
                        "WHERE fa.artist.id IN :artistIds AND f.endDate >= :today")
        List<FestivalArtist> findByArtist_IdInAndFestival_EndDateGreaterThanEqual(
                        @Param("artistIds") List<Long> artistIds, @Param("today") LocalDate today);
}
