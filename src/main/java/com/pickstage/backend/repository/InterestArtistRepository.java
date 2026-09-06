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
}