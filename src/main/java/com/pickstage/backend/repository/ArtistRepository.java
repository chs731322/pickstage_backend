package com.pickstage.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

    // 영문명(공백 제거·소문자)으로 찾기
    @Query("SELECT a FROM Artist a WHERE LOWER(REPLACE(a.englishName, ' ', '')) = :normalized")
    List<Artist> findByNormalizedEnglishName(@Param("normalized") String normalized);
}
