package com.pickstage.backend.service;

import com.pickstage.backend.dto.AdminFestivalRequest;
import com.pickstage.backend.dto.ArtistInfo;
import com.pickstage.backend.dto.FestivalCalendarResponse;
import com.pickstage.backend.dto.FestivalDetailResponse;
import com.pickstage.backend.dto.FestivalListResponse;
import com.pickstage.backend.dto.TicketInfo;
import com.pickstage.backend.dto.LineupRequest;
import com.pickstage.backend.dto.TicketRequest;
import com.pickstage.backend.dto.VenueInfo;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.entity.FestivalImage;
import com.pickstage.backend.entity.FestivalTicket;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.Venue;
import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.FestivalImageRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.FestivalTicketRepository;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.service.KopisVenueService.KopisVenue;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class FestivalService {

        private final FestivalRepository festivalRepository;
        private final FestivalImageRepository festivalImageRepository;
        private final FestivalArtistRepository festivalArtistRepository;
        private final FestivalTicketRepository festivalTicketRepository;
        private final UserRepository userRepository;
        private final VenueRepository venueRepository;
        private final BookmarkRepository bookmarkRepository;
        private final NotificationRepository notificationRepository;
        private final PostRepository postRepository;
        private final ArtistMatchService artistMatchService;
        private final KopisVenueService kopisVenueService;

        public FestivalService(FestivalRepository festivalRepository,
                        FestivalImageRepository festivalImageRepository,
                        FestivalArtistRepository festivalArtistRepository,
                        FestivalTicketRepository festivalTicketRepository,
                        UserRepository userRepository,
                        VenueRepository venueRepository,
                        BookmarkRepository bookmarkRepository,
                        NotificationRepository notificationRepository,
                        PostRepository postRepository,
                        ArtistMatchService artistMatchService,
                        KopisVenueService kopisVenueService) {
                this.festivalRepository = festivalRepository;
                this.festivalImageRepository = festivalImageRepository;
                this.festivalArtistRepository = festivalArtistRepository;
                this.festivalTicketRepository = festivalTicketRepository;
                this.userRepository = userRepository;
                this.venueRepository = venueRepository;
                this.bookmarkRepository = bookmarkRepository;
                this.notificationRepository = notificationRepository;
                this.postRepository = postRepository;
                this.artistMatchService = artistMatchService;
                this.kopisVenueService = kopisVenueService;
        }

        // 공연 목록 조회
        // sort: latest(기본) / popular(끝난 공연은 뒤로) / start(시작 임박순) / ticket_open(앞으로 열릴 티켓, 임박순) / ticket_open_recent(이미 열린 티켓, 최근순)
        // upcomingOnly: true면 이미 끝난 공연(종료일이 오늘 이전)은 제외 (메인 배너, 티켓 오픈 일정용)
        // outdoor: true면 장소 이름에 공원·해변·광장 등이 들어간 야외 공연만
        @Transactional(readOnly = true)
        public Page<FestivalListResponse> getFestivals(
                        String region, String genre, String keyword,
                        String sort, boolean upcomingOnly, boolean outdoor, int page, int size) {

                if (page < 0 || size <= 0) {
                        throw new IllegalArgumentException("올바르지 않은 요청입니다.");
                }

                String sortKey = sort != null ? sort : "latest";
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime openAfter = null;
                LocalDateTime openBefore = null;

                Sort sorting = switch (sortKey) {
                        // 인기순: 이미 끝난 공연은 뒤로 보내고, 그 안에서 찜 수 -> 조회 수 순
                        case "popular" -> org.springframework.data.jpa.domain.JpaSort
                                        .unsafe(Sort.Direction.ASC, "(CASE WHEN f.endDate < CURRENT_DATE THEN 1 ELSE 0 END)")
                                        .and(Sort.by(Sort.Direction.DESC, "bookmarkCount", "viewCount"));
                        // 임박순: 진행 중 -> 곧 시작할 공연(시작이 빠른 순) -> 이미 끝난 공연(최근에 시작한 순) -> 날짜 없는 공연
                        case "start" -> org.springframework.data.jpa.domain.JpaSort
                                        .unsafe(Sort.Direction.ASC,
                                                        "(CASE WHEN f.startDate IS NULL THEN 3 WHEN f.endDate < CURRENT_DATE THEN 2 WHEN f.startDate <= CURRENT_DATE THEN 0 ELSE 1 END)")
                                        .and(org.springframework.data.jpa.domain.JpaSort.unsafe(Sort.Direction.ASC,
                                                        "(CASE WHEN f.endDate >= CURRENT_DATE THEN f.startDate END)"))
                                        .and(org.springframework.data.jpa.domain.JpaSort.unsafe(Sort.Direction.DESC,
                                                        "(CASE WHEN f.endDate < CURRENT_DATE THEN f.startDate END)"));
                        case "ticket_open" -> {
                                openAfter = now; // 오픈일이 없거나 이미 지난 공연은 제외
                                yield Sort.by(Sort.Direction.ASC, "ticketOpenDate");
                        }
                        case "ticket_open_recent" -> {
                                openBefore = now; // 이미 오픈한 공연 중 최근에 오픈한 순
                                yield Sort.by(Sort.Direction.DESC, "ticketOpenDate");
                        }
                        default -> Sort.by(Sort.Direction.DESC, "createdAt");
                };

                Pageable pageable = PageRequest.of(page, size, sorting);

                java.time.LocalDate endAfter = upcomingOnly ? java.time.LocalDate.now() : null;

                // genre는 "hiphop,edm"처럼 쉼표로 여러 개를 줄 수 있다
                java.util.List<String> genreList = genre == null ? java.util.List.of()
                                : java.util.Arrays.stream(genre.split(",")).map(String::trim).filter(g -> !g.isEmpty()).toList();
                boolean useGenres = !genreList.isEmpty();

                Page<Festival> festivals = festivalRepository.findByFilters(region, useGenres, useGenres ? genreList : java.util.List.of(""),
                                keyword, openAfter, openBefore, endAfter, outdoor, pageable);

                Map<Long, String> firstTicketLabels = sortKey.startsWith("ticket_open")
                                ? firstTicketLabels(festivals.getContent())
                                : Map.of();

                return festivals.map(festival -> FestivalListResponse.builder()
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
                                .ticketOpenAt(festival.getTicketOpenDate())
                                .ticketLabel(firstTicketLabels.get(festival.getId()))
                                .build());
        }

        // 공연별 첫 번째(sortOrder가 가장 낮은) 티켓 이름을 한 번의 쿼리로 가져온다
        private Map<Long, String> firstTicketLabels(List<Festival> festivals) {
                if (festivals.isEmpty()) {
                        return Map.of();
                }
                List<Long> ids = festivals.stream().map(Festival::getId).toList();
                Map<Long, String> labels = new HashMap<>();
                for (FestivalTicket ticket : festivalTicketRepository.findByFestivalIdInOrderBySortOrderAsc(ids)) {
                        labels.putIfAbsent(ticket.getFestival().getId(), ticket.getLabel());
                }
                return labels;
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

        // 관리자 공연 등록
        @Transactional
        public Long createFestival(Long requesterId, AdminFestivalRequest request) {
                requireAdmin(requesterId);

                if (festivalRepository.existsByTitle(request.getTitle())) {
                        throw new org.springframework.dao.DataIntegrityViolationException("이미 등록된 공연명입니다.");
                }

                Venue venue = null;
                if (request.getVenueId() != null) {
                        venue = venueRepository.findById(request.getVenueId())
                                        .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연장입니다."));
                } else {
                        // 공연장을 고르지 않고 이름만 온 경우: 같은 이름이 있으면 그걸 쓰고, 없으면 KOPIS에서 찾아 바로 등록한다
                        venue = findOrRegisterVenue(request.getVenueName());
                }

                // 자료에 주소·지역이 없으면 연결된 공연장의 값을 쓴다
                String address = isBlank(request.getAddress()) && venue != null ? venue.getAddress() : request.getAddress();
                String region = isBlank(request.getRegion()) && venue != null ? venue.getRegion() : request.getRegion();

                Festival festival = Festival.builder()
                                .title(request.getTitle())
                                .description(request.getDescription())
                                .venue(venue)
                                .venueName(request.getVenueName())
                                .address(address)
                                .region(region)
                                .startDate(request.getStartDate())
                                .endDate(request.getEndDate())
                                .genre(request.getGenre())
                                .posterUrl(request.getPosterImage())
                                .createdAt(LocalDateTime.now())
                                .build();

                Festival saved = festivalRepository.save(festival);

                saveImages(saved, request.getImages());
                saveTickets(saved, request.getTickets());
                saveLineup(saved, request.getLineup());

                return saved.getId();
        }

        // 이름으로 기존 공연장을 찾고, 없으면 KOPIS 공연시설 정보로 새 공연장을 만든다. KOPIS에서도 못 찾으면 null(공연장 이름만 저장).
        private Venue findOrRegisterVenue(String venueName) {
                if (isBlank(venueName)) {
                        return null;
                }
                List<Venue> venues = venueRepository.findAll();
                Venue existing = findByNormalizedName(venues, venueName);
                if (existing != null) {
                        return existing;
                }

                Optional<KopisVenue> found = kopisVenueService.find(venueName);
                if (found.isEmpty()) {
                        return null;
                }
                KopisVenue kopis = found.get();
                // KOPIS의 정식 이름이 이미 우리 공연장 목록에 있으면 그 공연장을 쓴다
                Venue sameName = findByNormalizedName(venues, kopis.getName());
                if (sameName != null) {
                        return sameName;
                }
                LocalDateTime now = LocalDateTime.now();
                return venueRepository.save(Venue.builder()
                                .name(kopis.getName())
                                .address(kopis.getAddress())
                                .region(kopis.getRegion())
                                .latitude(kopis.getLatitude())
                                .longitude(kopis.getLongitude())
                                .capacity(kopis.getCapacity())
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
        }

        private static Venue findByNormalizedName(List<Venue> venues, String name) {
                String target = normalizeVenueName(name);
                if (target.isEmpty()) {
                        return null;
                }
                for (Venue v : venues) {
                        if (normalizeVenueName(v.getName()).equals(target)) {
                                return v;
                        }
                }
                return null;
        }

        private static String normalizeVenueName(String name) {
                return name == null ? "" : name.replaceAll("[\\s·\\-_.,()\\[\\]]", "").toLowerCase();
        }

        private static boolean isBlank(String s) {
                return s == null || s.isBlank();
        }

        // 관리자 공연 수정
        @Transactional
        public void updateFestival(Long requesterId, Long festivalId, AdminFestivalRequest request) {
                requireAdmin(requesterId);

                Festival festival = festivalRepository.findById(festivalId)
                                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));

                if (request.getTitle() != null) {
                        festival.setTitle(request.getTitle());
                }
                if (request.getDescription() != null) {
                        festival.setDescription(request.getDescription());
                }
                if (request.getVenueId() != null) {
                        Venue venue = venueRepository.findById(request.getVenueId())
                                        .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연장입니다."));
                        festival.setVenue(venue);
                }
                // 공연장 이름만 보낸 경우: 같은 이름의 공연장을 연결하고, 없으면 KOPIS 정보로 새로 등록한다 (등록할 때와 같은 규칙).
                // 이름이 그대로여도 아직 공연장에 연결되지 않은 공연이면 연결을 시도한다.
                if (request.getVenueId() == null && !isBlank(request.getVenueName())
                                && (festival.getVenue() == null
                                                || !request.getVenueName().equals(festival.getVenueName()))) {
                        Venue linked = findOrRegisterVenue(request.getVenueName());
                        if (linked != null) {
                                festival.setVenue(linked);
                                if (request.getAddress() == null && linked.getAddress() != null) {
                                        festival.setAddress(linked.getAddress());
                                }
                                if (request.getRegion() == null && linked.getRegion() != null) {
                                        festival.setRegion(linked.getRegion());
                                }
                        }
                }
                if (request.getVenueName() != null) {
                        festival.setVenueName(request.getVenueName());
                }
                if (request.getAddress() != null) {
                        festival.setAddress(request.getAddress());
                }
                if (request.getRegion() != null) {
                        festival.setRegion(request.getRegion());
                }
                if (request.getStartDate() != null) {
                        festival.setStartDate(request.getStartDate());
                }
                if (request.getEndDate() != null) {
                        festival.setEndDate(request.getEndDate());
                }
                if (request.getGenre() != null) {
                        festival.setGenre(request.getGenre());
                }
                if (request.getPosterImage() != null) {
                        festival.setPosterUrl(request.getPosterImage());
                }

                festivalRepository.save(festival);

                if (request.getImages() != null) {
                        festivalImageRepository.deleteByFestivalId(festivalId);
                        saveImages(festival, request.getImages());
                }
                if (request.getTickets() != null) {
                        festivalTicketRepository.deleteByFestivalId(festivalId);
                        saveTickets(festival, request.getTickets());
                }
                if (request.getLineup() != null) {
                        festivalArtistRepository.deleteByFestivalId(festivalId);
                        saveLineup(festival, request.getLineup());
                }
        }

        // 관리자 공연 삭제 (하드 삭제)
        @Transactional
        public void deleteFestival(Long requesterId, Long festivalId) {
                requireAdmin(requesterId);

                Festival festival = festivalRepository.findById(festivalId)
                                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));

                // FK가 NO ACTION인 것들은 먼저 정리해야 진짜 삭제가 가능함
                bookmarkRepository.deleteByFestivalId(festivalId);
                festivalImageRepository.deleteByFestivalId(festivalId);
                notificationRepository.deleteByFestivalId(festivalId);
                // 게시글은 지우지 않고 공연 연결만 해제 (사용자가 쓴 글이라 삭제하면 안 됨)
                postRepository.detachFestivalId(festivalId);

                // festival_artists / festival_tickets는 FK가 CASCADE라 자동으로 같이 삭제됨
                festivalRepository.delete(festival);
        }

        // 라인업 저장: 기존 아티스트와 매칭하고, 없으면 새로 만든다 (같은 아티스트는 한 번만)
        private void saveLineup(Festival festival, List<LineupRequest> lineup) {
                if (lineup == null || lineup.isEmpty()) {
                        return;
                }
                Set<Long> seen = new HashSet<>();
                int order = 0;
                for (LineupRequest entry : lineup) {
                        if (entry.getArtistName() == null || entry.getArtistName().isBlank()) {
                                continue;
                        }
                        // "1차 라인업: A, B, C"처럼 한 칸에 여러 명이 들어와도 한 명씩 나눠 등록한다
                        for (String name : FestivalDraftService.splitLineupName(entry.getArtistName())) {
                                Artist artist = artistMatchService.findOrCreate(name);
                                if (!seen.add(artist.getId())) {
                                        continue;
                                }
                                festivalArtistRepository.save(FestivalArtist.builder()
                                                .festival(festival)
                                                .artist(artist)
                                                .isHeadliner(false)
                                                .performanceDate(entry.getPerformanceDate())
                                                .billingOrder(++order)
                                                .source("admin")
                                                .build());
                        }
                }
        }

        private void requireAdmin(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
                if (!user.getRole().equals(User.Role.admin)) {
                        throw new AccessDeniedException("관리자 권한이 없습니다.");
                }
        }

        private void saveImages(Festival festival, List<String> images) {
                if (images == null) {
                        return;
                }
                for (int i = 0; i < images.size(); i++) {
                        festivalImageRepository.save(FestivalImage.builder()
                                        .festival(festival)
                                        .imageUrl(images.get(i))
                                        .sortOrder(i)
                                        .build());
                }
        }

        private void saveTickets(Festival festival, List<TicketRequest> tickets) {
                if (tickets == null) {
                        return;
                }
                for (int i = 0; i < tickets.size(); i++) {
                        TicketRequest t = tickets.get(i);
                        festivalTicketRepository.save(FestivalTicket.builder()
                                        .festival(festival)
                                        .label(t.getLabel())
                                        .vendorName(t.getVendorName())
                                        .price(t.getPrice())
                                        .bookingUrl(t.getBookingUrl())
                                        .openAt(t.getOpenAt())
                                        .sortOrder(i)
                                        .build());
                }
        }
}