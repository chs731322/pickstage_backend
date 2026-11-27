package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import com.pickstage.backend.dto.FestivalDraftRequest;
import com.pickstage.backend.dto.FestivalDraftResponse;
import com.pickstage.backend.dto.FestivalDraftResponse.ArtistMatch;
import com.pickstage.backend.dto.FestivalDraftResponse.Draft;
import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;
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

class FestivalUpdateSuggestionServiceTest {

    private static final Long ADMIN = 1L;
    private static final Long FESTIVAL = 10L;
    private static final LocalDate DAY = LocalDate.of(2027, 5, 1);

    private FestivalUpdateSuggestionRepository suggestions;
    private FestivalRepository festivals;
    private FestivalArtistRepository lineup;
    private FestivalTicketRepository tickets;
    private UserRepository users;
    private FestivalDraftService draftService;
    private ArtistMatchService artists;
    private FestivalUpdateSuggestionService service;

    @BeforeEach
    void setUp() {
        suggestions = mock(FestivalUpdateSuggestionRepository.class);
        festivals = mock(FestivalRepository.class);
        lineup = mock(FestivalArtistRepository.class);
        tickets = mock(FestivalTicketRepository.class);
        users = mock(UserRepository.class);
        draftService = mock(FestivalDraftService.class);
        artists = mock(ArtistMatchService.class);
        service = new FestivalUpdateSuggestionService(suggestions, festivals, lineup, tickets, users, draftService, artists);

        when(users.findById(ADMIN)).thenReturn(Optional.of(User.builder().id(ADMIN).role(User.Role.admin).build()));
        when(festivals.findById(FESTIVAL)).thenReturn(Optional.of(festival()));
        // 이미 등록된 것: 아티스트 1번, 멜론 1일권
        when(lineup.findByFestivalId(FESTIVAL)).thenReturn(List.of(
                FestivalArtist.builder().artist(Artist.builder().id(1L).name("기존밴드").build()).build()));
        when(tickets.findByFestivalIdOrderBySortOrderAsc(FESTIVAL)).thenReturn(List.of(
                FestivalTicket.builder().vendorName("멜론").label("1일권").build()));
        // 공지에서 읽은 것: 기존밴드 + 새밴드, 1일권 + 3일권
        when(draftService.createDraft(eq(ADMIN), any())).thenReturn(FestivalDraftResponse.builder()
                .draft(Draft.builder()
                        .lineup(List.of(new LineupRequest("기존밴드", DAY), new LineupRequest("새밴드", DAY)))
                        .tickets(List.of(
                                TicketDraft.builder().vendorName("멜론").label("1일권").build(),
                                TicketDraft.builder().vendorName("멜론").label("3일권").price(150000).build()))
                        .build())
                .artistMatches(List.of(
                        ArtistMatch.builder().name("기존밴드").artistId(1L).build(),
                        ArtistMatch.builder().name("새밴드").newArtist(true).build()))
                .build());
    }

    private static Festival festival() {
        return Festival.builder().id(FESTIVAL).title("테스트 페스티벌").build();
    }

    @SuppressWarnings("unchecked")
    private List<FestivalUpdateSuggestion> created() {
        service.createSuggestions(ADMIN, FESTIVAL, new FestivalDraftRequest("2차 라인업 공지", null, null));
        ArgumentCaptor<List<FestivalUpdateSuggestion>> saved = ArgumentCaptor.forClass(List.class);
        verify(suggestions).saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    void 새로_생긴_아티스트와_티켓만_대기_제안이_된다() {
        List<FestivalUpdateSuggestion> result = created();

        assertEquals(2, result.size());
        FestivalUpdateSuggestion artist = result.get(0);
        assertEquals(Type.LINEUP, artist.getType());
        assertEquals("새밴드", artist.getArtistName());
        assertEquals(DAY, artist.getPerformanceDate());
        FestivalUpdateSuggestion ticket = result.get(1);
        assertEquals(Type.TICKET, ticket.getType());
        assertEquals("3일권", ticket.getLabel());
        assertEquals(150000, ticket.getPrice());
        for (FestivalUpdateSuggestion s : result) {
            assertEquals(FESTIVAL, s.getFestivalId());
            assertEquals(Status.PENDING, s.getStatus());
            assertNotNull(s.getCreatedAt());
        }
    }

    @Test
    void 이미_대기중이거나_거절한_것은_다시_제안하지_않는다() {
        when(suggestions.findByFestivalIdAndStatusIn(eq(FESTIVAL), anyCollection())).thenReturn(List.of(
                FestivalUpdateSuggestion.builder().type(Type.LINEUP).status(Status.REJECTED).artistName("새 밴드").build(),
                FestivalUpdateSuggestion.builder().type(Type.TICKET).status(Status.PENDING).vendorName("멜론").label("3일권").build()));

        assertEquals(0, created().size());
    }

    @Test
    void 목록은_공연_이름과_함께_보여준다() {
        when(suggestions.findByStatusOrderByCreatedAtDesc(Status.PENDING)).thenReturn(List.of(
                FestivalUpdateSuggestion.builder().id(5L).festivalId(FESTIVAL).type(Type.LINEUP).status(Status.PENDING).artistName("새밴드").build()));
        when(festivals.findAllById(any())).thenReturn(List.of(festival()));

        var result = service.getSuggestions(ADMIN, Status.PENDING);

        assertEquals(1, result.size());
        assertEquals("테스트 페스티벌", result.get(0).getFestivalTitle());
    }

    @Test
    void 라인업을_승인하면_맨_뒤에_추가되고_승인_상태가_된다() {
        FestivalUpdateSuggestion s = pending(Type.LINEUP);
        when(artists.findOrCreate("새밴드")).thenReturn(Artist.builder().id(2L).name("새밴드").build());

        service.approve(ADMIN, 5L);

        ArgumentCaptor<FestivalArtist> saved = ArgumentCaptor.forClass(FestivalArtist.class);
        verify(lineup).save(saved.capture());
        assertEquals(2, saved.getValue().getBillingOrder());
        assertEquals(Status.APPROVED, s.getStatus());
        assertEquals(ADMIN, s.getHandledBy());
    }

    @Test
    void 티켓을_승인하면_맨_뒤에_추가된다() {
        pending(Type.TICKET);

        service.approve(ADMIN, 5L);

        ArgumentCaptor<FestivalTicket> saved = ArgumentCaptor.forClass(FestivalTicket.class);
        verify(tickets).save(saved.capture());
        assertEquals(1, saved.getValue().getSortOrder());
        assertEquals("3일권", saved.getValue().getLabel());
    }

    @Test
    void 거절하면_공연은_그대로_두고_거절_상태가_된다() {
        FestivalUpdateSuggestion s = pending(Type.TICKET);

        service.reject(ADMIN, 5L);

        assertEquals(Status.REJECTED, s.getStatus());
        verify(tickets, never()).save(any());
    }

    @Test
    void 이미_처리한_제안은_다시_승인할_수_없다() {
        when(suggestions.findById(5L)).thenReturn(Optional.of(FestivalUpdateSuggestion.builder()
                .id(5L).festivalId(FESTIVAL).type(Type.TICKET).status(Status.APPROVED).build()));

        assertThrows(IllegalStateException.class, () -> service.approve(ADMIN, 5L));
        verify(tickets, never()).save(any());
    }

    @Test
    void 관리자가_아니면_막는다() {
        when(users.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).role(User.Role.user).build()));

        assertThrows(AccessDeniedException.class, () -> service.getSuggestions(2L, Status.PENDING));
    }

    private FestivalUpdateSuggestion pending(Type type) {
        FestivalUpdateSuggestion s = FestivalUpdateSuggestion.builder()
                .id(5L).festivalId(FESTIVAL).type(type).status(Status.PENDING)
                .artistName("새밴드").vendorName("멜론").label("3일권").build();
        when(suggestions.findById(5L)).thenReturn(Optional.of(s));
        return s;
    }
}
