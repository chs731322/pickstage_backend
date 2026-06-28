package com.pickstage.backend.service;

import com.pickstage.backend.dto.ConcertDetailResponse;
import com.pickstage.backend.dto.ConcertListResponse;
import com.pickstage.backend.dto.VenueInfo;
import com.pickstage.backend.entity.Concert;
import com.pickstage.backend.repository.ConcertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConcertService {

    private final ConcertRepository concertRepository;

    public ConcertService(ConcertRepository concertRepository) {
        this.concertRepository = concertRepository;
    }

    // 공연 목록 조회
    public Page<ConcertListResponse> getConcerts(
            String region, String genre, String keyword,
            String sort, int page, int size) {

        // 정렬 설정
        Sort sorting = switch (sort != null ? sort : "latest") {
            case "popular" -> Sort.by(Sort.Direction.DESC, "bookmarkCount");
            case "ticket_open" -> Sort.by(Sort.Direction.ASC, "ticketOpenAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Pageable pageable = PageRequest.of(page, size, sorting);

        // 필터링 조회 후 DTO 변환
        return concertRepository.findByFilters(region, genre, keyword, pageable)
                .map(concert -> ConcertListResponse.builder()
                        .id(concert.getId())
                        .title(concert.getTitle())
                        .posterImage(concert.getPosterImage())
                        .startDate(concert.getStartDate())
                        .endDate(concert.getEndDate())
                        .region(concert.getRegion())
                        .genre(concert.getGenre())
                        .isFestival(concert.isFestival())
                        .venueName(concert.getVenue() != null ? concert.getVenue().getName() : null)
                        .bookmarkCount(concert.getBookmarkCount())
                        .build());
    }

    // 공연 상세 조회
    @Transactional
    public ConcertDetailResponse getConcert(Long id) {

        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연입니다."));

        return ConcertDetailResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .posterImage(concert.getPosterImage())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .region(concert.getRegion())
                .genre(concert.getGenre())
                .isFestival(concert.isFestival())
                .description(concert.getDescription())
                .ticketOpenAt(concert.getTicketOpenAt())
                .ticketLink(concert.getTicketLink())
                .externalUrl(concert.getExternalUrl())
                .viewCount(concert.getViewCount())
                .bookmarkCount(concert.getBookmarkCount())
                .venue(concert.getVenue() != null ? VenueInfo.builder()
                        .id(concert.getVenue().getId())
                        .name(concert.getVenue().getName())
                        .address(concert.getVenue().getAddress())
                        .region(concert.getVenue().getRegion())
                        .latitude(concert.getVenue().getLatitude())
                        .longitude(concert.getVenue().getLongitude())
                        .build() : null)
                .build();
    }
}