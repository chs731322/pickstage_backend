package com.pickstage.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.FestivalDraftRequest;
import com.pickstage.backend.dto.FestivalDraftResponse;
import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;
import com.pickstage.backend.dto.FestivalUpdateSuggestionResponse;
import com.pickstage.backend.dto.LineupRequest;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.entity.FestivalTicket;
import com.pickstage.backend.entity.FestivalUpdateSuggestion;
import com.pickstage.backend.entity.FestivalUpdateSuggestion.Status;
import com.pickstage.backend.entity.FestivalUpdateSuggestion.Type;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.FestivalTicketRepository;
import com.pickstage.backend.repository.FestivalUpdateSuggestionRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.service.FestivalDiffService.ArtistCandidate;

// 이미 등록한 공연의 새 공지를 읽어 "새로 생긴 것"만 제안으로 쌓고, 관리자가 승인하면 공연에 반영한다. (삭제는 하지 않는다)
@Service
public class FestivalUpdateSuggestionService {

    private final FestivalUpdateSuggestionRepository suggestionRepository;
    private final FestivalRepository festivalRepository;
    private final FestivalArtistRepository festivalArtistRepository;
    private final FestivalTicketRepository festivalTicketRepository;
    private final UserRepository userRepository;
    private final FestivalDraftService festivalDraftService;
    private final ArtistMatchService artistMatchService;

    public FestivalUpdateSuggestionService(FestivalUpdateSuggestionRepository suggestionRepository,
            FestivalRepository festivalRepository, FestivalArtistRepository festivalArtistRepository,
            FestivalTicketRepository festivalTicketRepository, UserRepository userRepository,
            FestivalDraftService festivalDraftService, ArtistMatchService artistMatchService) {
        this.suggestionRepository = suggestionRepository;
        this.festivalRepository = festivalRepository;
        this.festivalArtistRepository = festivalArtistRepository;
        this.festivalTicketRepository = festivalTicketRepository;
        this.userRepository = userRepository;
        this.festivalDraftService = festivalDraftService;
        this.artistMatchService = artistMatchService;
    }

    // AI 호출이 오래 걸려서 @Transactional 을 걸지 않는다 (DB 연결을 붙잡지 않도록. 저장은 saveAll 이 한 번에 한다)
    public List<FestivalUpdateSuggestionResponse> createSuggestions(Long adminId, Long festivalId,
            FestivalDraftRequest request) {
        requireAdmin(adminId);
        Festival festival = findFestival(festivalId);
        FestivalDraftResponse found = festivalDraftService.createDraft(adminId,
                withTitle(request, festival.getTitle()));

        Set<Long> currentArtistIds = festivalArtistRepository.findByFestivalId(festivalId).stream()
                .map(link -> link.getArtist().getId())
                .collect(Collectors.toSet());

        List<FestivalUpdateSuggestion> already = suggestionRepository.findByFestivalIdAndStatusIn(festivalId,
                List.of(Status.PENDING, Status.REJECTED));

        // 새 아티스트: 라인업에 없고, 이미 제안했던 이름도 아닌 것
        List<ArtistCandidate> candidates = found.getArtistMatches().stream()
                .map(match -> new ArtistCandidate(match.getName(), match.getArtistId()))
                .toList();
        Set<String> suggestedNames = new HashSet<>();
        for (FestivalUpdateSuggestion s : already) {
            if (s.getType() == Type.LINEUP) {
                suggestedNames.add(ArtistMatchService.normalize(s.getArtistName()));
            }
        }
        Map<String, LocalDate> dates = new HashMap<>();
        for (LineupRequest entry : found.getDraft().getLineup()) {
            if (entry.getPerformanceDate() != null) {
                dates.putIfAbsent(ArtistMatchService.normalize(entry.getArtistName()), entry.getPerformanceDate());
            }
        }

        // 새 티켓: 등록된 티켓 + 이미 제안했던 티켓과 비교
        List<FestivalTicket> known = new ArrayList<>(
                festivalTicketRepository.findByFestivalIdOrderBySortOrderAsc(festivalId));
        for (FestivalUpdateSuggestion s : already) {
            if (s.getType() == Type.TICKET) {
                known.add(FestivalTicket.builder().vendorName(s.getVendorName()).label(s.getLabel()).build());
            }
        }

        List<FestivalUpdateSuggestion> suggestions = new ArrayList<>();
        for (ArtistCandidate artist : FestivalDiffService.newArtists(currentArtistIds, candidates)) {
            String key = ArtistMatchService.normalize(artist.name());
            if (!suggestedNames.contains(key)) {
                suggestions.add(lineupSuggestion(festivalId, artist, dates.get(key)));
            }
        }
        for (TicketDraft ticket : FestivalDiffService.newTickets(known, found.getDraft().getTickets())) {
            suggestions.add(ticketSuggestion(festivalId, ticket));
        }

        suggestionRepository.saveAll(suggestions);
        return suggestions.stream()
                .map(s -> FestivalUpdateSuggestionResponse.of(s, festival.getTitle()))
                .toList();
    }

    public List<FestivalUpdateSuggestionResponse> getSuggestions(Long adminId, Status status) {
        requireAdmin(adminId);
        List<FestivalUpdateSuggestion> suggestions = suggestionRepository.findByStatusOrderByCreatedAtDesc(status);

        Map<Long, String> titles = new HashMap<>();
        festivalRepository
                .findAllById(suggestions.stream().map(FestivalUpdateSuggestion::getFestivalId).distinct().toList())
                .forEach(festival -> titles.put(festival.getId(), festival.getTitle()));
        return suggestions.stream()
                .map(s -> FestivalUpdateSuggestionResponse.of(s, titles.get(s.getFestivalId())))
                .toList();
    }

    @Transactional
    public void approve(Long adminId, Long suggestionId) {
        requireAdmin(adminId);
        FestivalUpdateSuggestion suggestion = findPending(suggestionId);
        Festival festival = findFestival(suggestion.getFestivalId());
        if (suggestion.getType() == Type.LINEUP) {
            addArtist(festival, suggestion);
        } else {
            addTicket(festival, suggestion);
        }
        suggestion.handle(Status.APPROVED, adminId);
    }

    @Transactional
    public void reject(Long adminId, Long suggestionId) {
        requireAdmin(adminId);
        FestivalUpdateSuggestion suggestion = findPending(suggestionId);
        suggestion.handle(Status.REJECTED, adminId);
    }

    // ---- 제안 만들기 ----

    private FestivalUpdateSuggestion lineupSuggestion(Long festivalId, ArtistCandidate artist,
            LocalDate performanceDate) {
        return FestivalUpdateSuggestion.builder()
                .festivalId(festivalId)
                .type(Type.LINEUP)
                .artistName(artist.name())
                .status(Status.PENDING)
                .artistId(artist.artistId())
                .performanceDate(performanceDate)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private FestivalUpdateSuggestion ticketSuggestion(Long festivalId, TicketDraft ticket) {
        return FestivalUpdateSuggestion.builder()
                .festivalId(festivalId)
                .type(Type.TICKET)
                .status(Status.PENDING)
                .label(ticket.getLabel())
                .vendorName(ticket.getVendorName())
                .price(ticket.getPrice())
                .bookingUrl(ticket.getBookingUrl())
                .openAt(ticket.getOpenAt())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ---- 승인했을 때 공연에 반영 ----

    private void addArtist(Festival festival, FestivalUpdateSuggestion suggestion) {
        Artist artist = artistMatchService.findOrCreate(suggestion.getArtistName());
        if (festivalArtistRepository.existsLink(festival.getId(), artist.getId())) {
            return;
        }
        festivalArtistRepository.save(FestivalArtist.builder()
                .festival(festival)
                .artist(artist)
                .isHeadliner(false)
                .performanceDate(suggestion.getPerformanceDate())
                .billingOrder(festivalArtistRepository.findByFestivalId(festival.getId()).size() + 1)
                .source("admin")
                .build());
    }

    private void addTicket(Festival festival, FestivalUpdateSuggestion suggestion) {
        festivalTicketRepository.save(FestivalTicket.builder()
                .festival(festival)
                .label(suggestion.getLabel())
                .vendorName(suggestion.getVendorName())
                .price(suggestion.getPrice())
                .bookingUrl(suggestion.getBookingUrl())
                .openAt(suggestion.getOpenAt())
                .sortOrder(festivalTicketRepository.findByFestivalIdOrderBySortOrderAsc(festival.getId()).size())
                .build());
    }

    // ---- 공통 ----

    // 승인·거절은 대기 중인 제안만 할 수 있다 (두 번 반영되지 않게)
    private FestivalUpdateSuggestion findPending(Long suggestionId) {
        FestivalUpdateSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 제안입니다."));
        if (suggestion.getStatus() != Status.PENDING) {
            throw new IllegalStateException("이미 처리한 제안입니다.");
        }
        return suggestion;
    }

    private Festival findFestival(Long festivalId) {
        return festivalRepository.findById(festivalId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));
    }

    // 초안 생성은 공연명이 없으면 실패하므로, 이미 아는 공연명을 앞에 붙여 준다
    private static FestivalDraftRequest withTitle(FestivalDraftRequest request, String title) {
        boolean hasText = request.getText() != null && !request.getText().isBlank();
        boolean hasImage = request.getImageBase64() != null && !request.getImageBase64().isBlank();
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("공지 글이나 이미지를 넣어주세요.");
        }
        String text = "공연명: " + title + "\n" + (hasText ? request.getText() : "");
        return new FestivalDraftRequest(text, request.getImageBase64(), request.getImageMimeType());
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
