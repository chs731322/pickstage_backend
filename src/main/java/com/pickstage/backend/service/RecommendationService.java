package com.pickstage.backend.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.pickstage.backend.dto.RecommendationResponse;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.InterestArtistRepository;

@Service
public class RecommendationService {
    private final InterestArtistRepository interestArtistRepository;
    private final FestivalArtistRepository festivalArtistRepository;

    public RecommendationService(InterestArtistRepository interestArtistRepository,
            FestivalArtistRepository festivalArtistRepository) {
        this.interestArtistRepository = interestArtistRepository;
        this.festivalArtistRepository = festivalArtistRepository;
    }

    // 유저의 관심 아티스트 기반 추천 공연 목록 조회
    public List<RecommendationResponse> getRecommendedFestivalsForUser(Long userId, int page, int size) {
        // 1. 유저의 관심 아티스트 id 목록
        List<Long> interestedArtistIds = interestArtistRepository.findArtistIdsByUserId(userId);
        if (interestedArtistIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 관심 아티스트가 라인업에 있고 아직 안 끝난 공연의 festival_artists row 전부
        List<FestivalArtist> matches = festivalArtistRepository
                .findByArtist_IdInAndFestival_EndDateGreaterThanEqual(interestedArtistIds, LocalDate.now());

        // 3. 같은 공연끼리 묶기 (Festival -> 그 공연에 매칭된 FestivalArtist 목록)
        Map<Festival, List<FestivalArtist>> groupedByFestival = matches.stream()
                .collect(Collectors.groupingBy(FestivalArtist::getFestival));

        // 4. matchCount(그룹 크기) 내림차순 -> startDate 오름차순으로 정렬
        List<RecommendationResponse> sorted = groupedByFestival.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Festival, List<FestivalArtist>> entry) -> entry.getValue().size())
                        .reversed()
                        .thenComparing(entry -> entry.getKey().getStartDate()))
                .map(entry -> {
                    Festival festival = entry.getKey();
                    List<String> matchedArtists = entry.getValue().stream()
                            .map(fa -> fa.getArtist().getName())
                            .toList();

                    return RecommendationResponse.builder()
                            .id(festival.getId())
                            .title(festival.getTitle())
                            .posterImage(festival.getPosterUrl())
                            .startDate(festival.getStartDate())
                            .endDate(festival.getEndDate())
                            .region(festival.getRegion())
                            .genre(festival.getGenre())
                            .venueName(festival.getVenueName())
                            .matchedArtists(matchedArtists)
                            .matchCount(matchedArtists.size())
                            .build();
                })
                .toList();

        // 5. 자바 리스트 단에서 페이지 자르기
        int fromIndex = Math.min(page * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());
        return sorted.subList(fromIndex, toIndex);
    }
}
