package com.pickstage.backend.service;

import com.pickstage.backend.dto.ArtistInfo;
import com.pickstage.backend.dto.FestivalCalendarResponse;
import com.pickstage.backend.dto.FestivalDetailResponse;
import com.pickstage.backend.dto.FestivalListResponse;
import com.pickstage.backend.dto.TicketInfo;
import com.pickstage.backend.dto.VenueInfo;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalImage;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.FestivalImageRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.FestivalTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

import java.util.List;

@Service
public class FestivalService {

        private final FestivalRepository festivalRepository;
        private final FestivalImageRepository festivalImageRepository;
        private final FestivalArtistRepository festivalArtistRepository;
        private final FestivalTicketRepository festivalTicketRepository;

        public FestivalService(FestivalRepository festivalRepository,
                        FestivalImageRepository festivalImageRepository,
                        FestivalArtistRepository festivalArtistRepository,
                        FestivalTicketRepository festivalTicketRepository) {
                this.festivalRepository = festivalRepository;
                this.festivalImageRepository = festivalImageRepository;
                this.festivalArtistRepository = festivalArtistRepository;
                this.festivalTicketRepository = festivalTicketRepository;
        }

        // 공연 목록 조회
        public Page<FestivalListResponse> getFestivals(
                        String region, String genre, String keyword,
                        String sort, int page, int size) {

                if (page < 0 || size <= 0) {
                        throw new IllegalArgumentException("올바르지 않은 요청입니다.");
                }

                Sort sorting = switch (sort != null ? sort : "latest") {
                        case "popular" -> Sort.by(Sort.Direction.DESC, "bookmarkCount");
                        case "ticket_open" -> Sort.by(Sort.Direction.ASC, "ticketOpenDate");
                        default -> Sort.by(Sort.Direction.DESC, "createdAt");
                };

                Pageable pageable = PageRequest.of(page, size, sorting);

                return festivalRepository.findByFilters(region, genre, keyword, pageable)
                                .map(festival -> FestivalListResponse.builder()
                                                .id(festival.getId())
                                                .title(festival.getTitle())
                                                .posterImage(festival.getPosterUrl())
                                                .startDate(festival.getStartDate())
                                                .endDate(festival.getEndDate())
                                                .region(festival.getRegion())
                                                .genre(festival.getGenre())
                                                .isFestival(true)
                                                .venueName(festival.getVenueName())
                                                .bookmarkCount(festival.getBookmarkCount())
                                                .build());
        }

        // 공연 상세 조회
        @Transactional
        public FestivalDetailResponse getFestival(Long id) {

                Festival festival = festivalRepository.findById(id)
                                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));

                festival.incrementViewCount();

                List<String> images = festivalImageRepository.findByFestivalIdOrderBySortOrderAsc(id)
                                .stream()
                                .map(FestivalImage::getImageUrl)
                                .toList();

                List<ArtistInfo> artists = festivalArtistRepository.findByFestivalId(id)
                                .stream()
                                .map(fa -> ArtistInfo.builder()
                                                .id(fa.getArtist().getId())
                                                .name(fa.getArtist().getName())
                                                .profileImage(fa.getArtist().getProfileImageUrl())
                                                .isHeadliner(fa.isHeadliner())
                                                .performanceDate(fa.getPerformanceDate())
                                                .build())
                                .toList();

                return FestivalDetailResponse.builder()
                                .id(festival.getId())
                                .title(festival.getTitle())
                                .posterImage(festival.getPosterUrl())
                                .images(images)
                                .startDate(festival.getStartDate())
                                .endDate(festival.getEndDate())
                                .region(festival.getRegion())
                                .genre(festival.getGenre())
                                .isFestival(true)
                                .description(festival.getDescription())
                                .ticketOpenAt(festival.getTicketOpenDate())
                                .ticketLink(festival.getBookingUrl())
                                .externalUrl(null)
                                .viewCount(festival.getViewCount())
                                .bookmarkCount(festival.getBookmarkCount())
                                .venue(festival.getVenue() != null
                                                ? VenueInfo.builder()
                                                                .id(festival.getVenue().getId())
                                                                .name(festival.getVenue().getName())
                                                                .address(festival.getVenue().getAddress())
                                                                .region(festival.getVenue().getRegion())
                                                                .latitude(festival.getVenue().getLatitude())
                                                                .longitude(festival.getVenue().getLongitude())
                                                                .build()
                                                : VenueInfo.builder()
                                                                .id(null)
                                                                .name(festival.getVenueName())
                                                                .address(festival.getAddress())
                                                                .region(festival.getRegion())
                                                                .latitude(null)
                                                                .longitude(null)
                                                                .build())
                                .artists(artists)
                                .tickets(festivalTicketRepository.findByFestivalIdOrderBySortOrderAsc(id).stream()
                                                .map(ticket -> TicketInfo.builder()
                                                                .label(ticket.getLabel())
                                                                .vendorName(ticket.getVendorName())
                                                                .price(ticket.getPrice())
                                                                .bookingUrl(ticket.getBookingUrl())
                                                                .openAt(ticket.getOpenAt())
                                                                .build())
                                                .toList())
                                .build();
        }

        // 북마크 수 증가
        public void incrementBookmarkCount(Long festivalId) {
                Festival festival = festivalRepository.findById(festivalId)
                                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));
                festival.incrementBookmarkCount();
                festivalRepository.save(festival);
        }

        // 북마크 수 감소
        public void decrementBookmarkCount(Long festivalId) {
                Festival festival = festivalRepository.findById(festivalId)
                                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));
                festival.decrementBookmarkCount();
                festivalRepository.save(festival);
        }

        /*
         * public List<FestivalCalendarResponse> getCalendar(int year, int month) { ...
         * }
         * month가 1~12 밖이면 IllegalArgumentException("올바르지 않은 요청입니다.") (400)
         * LocalDate startOfMonth = LocalDate.of(year, month, 1)
         * LocalDate endOfMonth =
         * startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth()) (그 달의 마지막 날 자동 계산 —
         * 28/29/30/31 신경 안 써도 됨)
         * 리포지토리 호출 후 FestivalCalendarResponse로 매핑해서 반환
         */

        public List<FestivalCalendarResponse> getCalendar(int year, int month) {
                if (month < 1 || month > 12) {
                        throw new IllegalArgumentException("올바르지 않은 요청입니다.");
                }

                java.time.LocalDate startOfMonth = java.time.LocalDate.of(year, month, 1);
                java.time.LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

                List<Festival> festivals = festivalRepository.findByDateRange(startOfMonth, endOfMonth);

                return festivals.stream()
                                .map(festival -> FestivalCalendarResponse.builder()
                                                .id(festival.getId())
                                                .title(festival.getTitle())
                                                .startDate(festival.getStartDate().toString())
                                                .endDate(festival.getEndDate().toString())
                                                .region(festival.getRegion())
                                                .posterImage(festival.getPosterUrl())
                                                .venueName(festival.getVenueName())
                                                .build())
                                .toList();
        }
}
