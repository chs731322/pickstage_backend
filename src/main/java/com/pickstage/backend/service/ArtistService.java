package com.pickstage.backend.service;

import com.pickstage.backend.dto.ArtistDetailResponse;
import com.pickstage.backend.dto.UpcomingConcertInfo;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.repository.ArtistRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final FestivalArtistRepository festivalArtistRepository;

    public ArtistService(ArtistRepository artistRepository,
                          FestivalArtistRepository festivalArtistRepository) {
        this.artistRepository = artistRepository;
        this.festivalArtistRepository = festivalArtistRepository;
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
                .profileImage(artist.getProfileImageUrl())
                .genre(artist.getGenrePrimary())
                .description(null)
                .upcomingConcerts(upcomingConcerts)
                .build();
    }
}
