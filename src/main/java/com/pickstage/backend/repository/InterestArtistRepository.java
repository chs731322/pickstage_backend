package com.pickstage.backend.repository;

import com.pickstage.backend.entity.InterestArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestArtistRepository extends JpaRepository<InterestArtist, Long> {

    // 유저 아이디와 아티스트 아이디로 관심 아티스트가 존재하는지 확인
    boolean existsByUserIdAndArtistId(Long userId, Long artistId);

    // 유저 아이디와 아티스트 아이디로 관심 아티스트 삭제
    void deleteByUserIdAndArtistId(Long userId, Long artistId);
}