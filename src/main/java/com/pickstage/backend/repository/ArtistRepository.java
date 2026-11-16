package com.pickstage.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pickstage.backend.entity.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findFirstByNormalizedName(String normalizedName);

    Optional<Artist> findFirstByName(String name);

    // 별칭 테이블(artist_aliases)로 찾기 (예: "넬" -> NELL)
    @Query(value = "SELECT a.* FROM artists a JOIN artist_aliases al ON al.artist_id = a.artist_id "
            + "WHERE al.normalized_alias = :normalized LIMIT 1", nativeQuery = true)
    Optional<Artist> findByAlias(@Param("normalized") String normalized);

    // 관리자 목록: 이름·영문명 검색 (keyword는 소문자로 넘긴다), 공연에 연결되지 않은 아티스트만 볼 수도 있다
    @Query("SELECT a FROM Artist a WHERE "
            + "(:keyword IS NULL OR LOWER(a.name) LIKE CONCAT('%', :keyword, '%') "
            + "OR LOWER(a.englishName) LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:unlinkedOnly = false OR NOT EXISTS (SELECT 1 FROM FestivalArtist fa WHERE fa.artist = a)) "
            + "AND (:noPhotoOnly = false OR a.profileImageUrl IS NULL OR a.profileImageUrl = '') "
            + "AND (:noSpotifyOnly = false OR a.spotifyUrl IS NULL OR a.spotifyUrl = '')")
    Page<Artist> searchForAdmin(@Param("keyword") String keyword, @Param("unlinkedOnly") boolean unlinkedOnly,
            @Param("noPhotoOnly") boolean noPhotoOnly, @Param("noSpotifyOnly") boolean noSpotifyOnly,
            Pageable pageable);

    // 공연 검색창의 아티스트 후보: 이름·영문명에 keyword(소문자)가 들어가고, 출연 공연이 하나라도 있는 아티스트.
    // 각 행은 [id, 이름, 영문명, 프로필 사진, 아직 끝나지 않은 출연 공연 수]
    @Query("SELECT a.id, a.name, a.englishName, a.profileImageUrl, "
            + "(SELECT COUNT(fa) FROM FestivalArtist fa WHERE fa.artist = a AND fa.festival.endDate >= :today) "
            + "FROM Artist a WHERE "
            + "(LOWER(a.name) LIKE CONCAT('%', :keyword, '%') OR LOWER(a.englishName) LIKE CONCAT('%', :keyword, '%')) "
            + "AND EXISTS (SELECT 1 FROM FestivalArtist fa2 WHERE fa2.artist = a)")
    List<Object[]> searchLinkedByKeyword(@Param("keyword") String keyword, @Param("today") java.time.LocalDate today,
            Pageable pageable);

    // 아티스트 목록: 아직 끝나지 않은 출연 공연이 하나라도 있는 아티스트.
    // 각 행은 [id, 이름, 영문명, 프로필 사진, 아직 끝나지 않은 출연 공연 수]
    @Query("SELECT a.id, a.name, a.englishName, a.profileImageUrl, "
            + "(SELECT COUNT(fa) FROM FestivalArtist fa WHERE fa.artist = a AND fa.festival.endDate >= :today) "
            + "FROM Artist a WHERE EXISTS "
            + "(SELECT 1 FROM FestivalArtist fa2 WHERE fa2.artist = a AND fa2.festival.endDate >= :today)")
    List<Object[]> findWithUpcomingFestivals(@Param("today") java.time.LocalDate today, Pageable pageable);

    // 별칭 정리 (삭제·합치기용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM artist_aliases WHERE artist_id = :artistId", nativeQuery = true)
    void deleteAliasesByArtistId(@Param("artistId") Long artistId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE artist_aliases SET artist_id = :target WHERE artist_id = :source", nativeQuery = true)
    void moveAliases(@Param("source") Long source, @Param("target") Long target);

    @Query(value = "SELECT COUNT(*) FROM artist_aliases WHERE artist_id = :artistId AND normalized_alias = :normalized",
            nativeQuery = true)
    long countAlias(@Param("artistId") Long artistId, @Param("normalized") String normalized);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO artist_aliases (artist_id, alias_name, normalized_alias, source, created_at) "
            + "VALUES (:artistId, :alias, :normalized, 'admin_merge', NOW())", nativeQuery = true)
    void addAlias(@Param("artistId") Long artistId, @Param("alias") String alias,
            @Param("normalized") String normalized);

    // 영문명(공백 제거·소문자)으로 찾기
    @Query("SELECT a FROM Artist a WHERE LOWER(REPLACE(a.englishName, ' ', '')) = :normalized")
    List<Artist> findByNormalizedEnglishName(@Param("normalized") String normalized);
}
