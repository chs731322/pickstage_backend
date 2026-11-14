package com.pickstage.backend.service;

import com.pickstage.backend.dto.ArtistDetailResponse;
import com.pickstage.backend.dto.ArtistSearchResponse;
import com.pickstage.backend.dto.UpcomingConcertInfo;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.repository.ArtistRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final FestivalArtistRepository festivalArtistRepository;
    private final ObjectMapper objectMapper;

    public ArtistService(ArtistRepository artistRepository,
                          FestivalArtistRepository festivalArtistRepository,
                          ObjectMapper objectMapper) {
        this.artistRepository = artistRepository;
        this.festivalArtistRepository = festivalArtistRepository;
        this.objectMapper = objectMapper;
    }

    // 아티스트 상세 조회
    // 공연 검색창에 보여줄 아티스트 후보. 이름이 똑같은 아티스트 -> 예정 공연이 많은 순 -> 이름순
    static final int SEARCH_MAX_SIZE = 10;
    // 한 글자 검색(예: "향")도 허용하되, 너무 많이 가져오지 않게 후보 수를 제한한다
    static final int SEARCH_CANDIDATE_LIMIT = 300;

    public List<ArtistSearchResponse> searchArtists(String keyword, int size) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(Math.max(size, 1), SEARCH_MAX_SIZE);

        List<Object[]> rows = artistRepository.searchLinkedByKeyword(
                normalized, LocalDate.now(), org.springframework.data.domain.PageRequest.of(0, SEARCH_CANDIDATE_LIMIT));

        return rows.stream()
                .map(row -> ArtistSearchResponse.builder()
                        .id((Long) row[0])
                        .name((String) row[1])
                        .englishName((String) row[2])
                        .profileImage((String) row[3])
                        .upcomingCount(((Number) row[4]).intValue())
                        .build())
                .sorted(java.util.Comparator
                        .comparing((ArtistSearchResponse a) -> !isExactName(a, normalized))
                        .thenComparing(java.util.Comparator.comparingInt(ArtistSearchResponse::getUpcomingCount).reversed())
                        .thenComparing(a -> a.getName() == null ? "" : a.getName()))
                .limit(limit)
                .toList();
    }

    private static boolean isExactName(ArtistSearchResponse artist, String normalizedKeyword) {
        return normalizedKeyword.equals(artist.getName() == null ? null : artist.getName().toLowerCase())
                || normalizedKeyword.equals(artist.getEnglishName() == null ? null : artist.getEnglishName().toLowerCase());
    }

    // 검색 엔진 사이트맵에 넣을 아티스트 id 목록 (공개 정보)
    public List<Long> getArtistIdsForSitemap() {
        return festivalArtistRepository.findLinkedArtistIds();
    }

    public ArtistDetailResponse getArtist(Long id) {

        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 아티스트입니다."));

        LocalDate today = LocalDate.now();
        List<UpcomingConcertInfo> upcomingConcerts = toConcertInfos(festivalArtistRepository.findUpcomingByArtistId(id, today));
        List<UpcomingConcertInfo> pastConcerts = toConcertInfos(festivalArtistRepository.findPastByArtistId(id, today));

        return ArtistDetailResponse.builder()
                .id(artist.getId())
                .name(artist.getName())
                .englishName(artist.getEnglishName())
                .artistType(artist.getArtistType())
                .profileImage(artist.getProfileImageUrl())
                .genre(artist.getGenrePrimary())
                .genreTags(parseGenreTags(artist.getGenreTags()))
                .countryCode(artist.getCountryCode())
                .officialUrl(artist.getOfficialUrl())
                .instagramUrl(artist.getInstagramUrl())
                .spotifyUrl(artist.getSpotifyUrl())
                .description(null)
                .upcomingConcerts(upcomingConcerts)
                .pastConcerts(pastConcerts)
                .build();
    }

    private List<UpcomingConcertInfo> toConcertInfos(List<FestivalArtist> festivalArtists) {
        return festivalArtists.stream()
                .map(FestivalArtist::getFestival)
                .map(festival -> UpcomingConcertInfo.builder()
                        .id(festival.getId())
                        .title(festival.getTitle())
                        .posterImage(festival.getPosterUrl())
                        .startDate(festival.getStartDate())
                        .endDate(festival.getEndDate())
                        .region(festival.getRegion())
                        .venueName(festival.getVenueName())
                        .build())
                .toList();
    }

    // genre_tags는 DB에 JSON 문자열로 저장돼 있어 List<String>으로 파싱
    private List<String> parseGenreTags(String genreTagsJson) {
        if (genreTagsJson == null || genreTagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return List.of(objectMapper.readValue(genreTagsJson, String[].class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
