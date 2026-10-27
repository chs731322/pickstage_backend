package com.pickstage.backend.repository;

import com.pickstage.backend.entity.InterestArtist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterestArtistRepository extends JpaRepository<InterestArtist, Long> {

    // 유저 아이디와 아티스트 아이디로 관심 아티스트가 존재하는지 확인
    boolean existsByUserIdAndArtistId(Long userId, Long artistId);

    // 유저 아이디와 아티스트 아이디로 관심 아티스트 삭제
    void deleteByUserIdAndArtistId(Long userId, Long artistId);

    @Query("SELECT ia.artistId FROM InterestArtist ia WHERE ia.userId = :userId")
    List<Long> findArtistIdsByUserId(@Param("userId") Long userId);

    // 내 관심 아티스트 목록 최신순 조회
    List<InterestArtist> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 관리자 아티스트 목록: 아티스트별 관심 등록 수
    @Query("SELECT ia.artistId, COUNT(ia) FROM InterestArtist ia WHERE ia.artistId IN :artistIds GROUP BY ia.artistId")
    List<Object[]> countByArtistIds(@Param("artistIds") List<Long> artistIds);

    // 아티스트를 지울 때 관심 등록도 함께 지운다
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InterestArtist ia WHERE ia.artistId = :artistId")
    void deleteByArtistId(@Param("artistId") Long artistId);

    // 합치기: 두 아티스트를 모두 담은 회원은 이쪽 행을 지우고, 나머지는 대상으로 옮긴다
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM interest_artists WHERE artist_id = :source AND user_id IN "
            + "(SELECT user_id FROM (SELECT user_id FROM interest_artists WHERE artist_id = :target) t)", nativeQuery = true)
    void deleteDuplicatesBeforeMerge(@Param("source") Long source, @Param("target") Long target);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE interest_artists SET artist_id = :target WHERE artist_id = :source", nativeQuery = true)
    void moveToArtist(@Param("source") Long source, @Param("target") Long target);

    // 회원 탈퇴 시 해당 사용자의 관심 아티스트 일괄 삭제
    void deleteByUserId(Long userId);
}
