package com.pickstage.backend.service;

import com.pickstage.backend.entity.InterestArtist;
import com.pickstage.backend.repository.ArtistRepository;
import com.pickstage.backend.repository.InterestArtistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class InterestArtistService {
    private final InterestArtistRepository interestArtistRepository;
    private final ArtistRepository artistRepository;

    public InterestArtistService(InterestArtistRepository interestArtistRepository,
                                  ArtistRepository artistRepository) {
        this.interestArtistRepository = interestArtistRepository;
        this.artistRepository = artistRepository;
    }

    // 유저 아이디와 아티스트 아이디로 관심 아티스트가 존재하는지 확인
    public boolean isInterestArtistExists(Long userId, Long artistId) {
        return interestArtistRepository.existsByUserIdAndArtistId(userId, artistId);
    }

    // 관심 아티스트 추가
    public void addInterestArtist(Long userId, Long artistId) {
        // 아티스트가 존재하는지 확인
        if (!artistRepository.existsById(artistId)) {
            throw new IllegalStateException("존재하지 않는 아티스트입니다.");
        }

        // 이미 관심 아티스트로 등록된 아티스트인지 확인
        if (interestArtistRepository.existsByUserIdAndArtistId(userId, artistId)) {
            throw new org.springframework.dao.DataIntegrityViolationException("이미 관심 아티스트입니다.");
        }

        // 관심 아티스트 추가
        interestArtistRepository.save(InterestArtist.builder()
                .userId(userId)
                .artistId(artistId)
                .createdAt(java.time.LocalDateTime.now())
                .build());

    }

    // 관심 아티스트 삭제
    @Transactional
    public void deleteInterestArtist(Long userId, Long artistId) {
        // 아티스트가 존재하는지 확인
        if (!artistRepository.existsById(artistId)) {
            throw new IllegalStateException("존재하지 않는 아티스트입니다.");
        }

        // 관심 아티스트 삭제 (등록되어 있지 않아도 조용히 성공 처리)
        interestArtistRepository.deleteByUserIdAndArtistId(userId, artistId);
    }
}
