package com.pickstage.backend.service;

import com.pickstage.backend.dto.ArtistDetailResponse;
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
    public ArtistDetailResponse getArtist(Long id) {

        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 아티스트입니다."));

        List<UpcomingConcertInfo> upcomingConcerts = festivalArtistRepository
                .findUpcomingByArtistId(id, LocalDate.now())
                .stream()
                .map(FestivalArtist::getFestival)
                .map(festival -> UpcomingConcertInfo.builder()
                        .id(festival.getId())
                        .title(festival.getTitle())
                        .posterImage(festival.getPosterUrl())
                        .startDate(festival.getStartDate())
                        .region(festival.getRegion())
                        .venueName(festival.getVenueName())
                        .build())
                .toList();

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
                .build();
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
