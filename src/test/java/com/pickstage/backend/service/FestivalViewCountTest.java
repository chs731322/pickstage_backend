package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.repository.BookmarkRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.FestivalImageRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.FestivalTicketRepository;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueRepository;

// 공연 상세 조회수: 인기순 정렬에 쓰이므로 새로고침이나 검색 로봇으로 부풀지 않는지 확인한다.
class FestivalViewCountTest {

    private Festival festival;
    private FestivalService service;
    private ViewGuard viewGuard;

    @BeforeEach
    void setUp() {
        festival = Festival.builder().id(1L).title("테스트 공연").build();
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        when(festivalRepository.findById(1L)).thenReturn(Optional.of(festival));
        viewGuard = new ViewGuard();
        service = new FestivalService(festivalRepository, mock(FestivalImageRepository.class),
                mock(FestivalArtistRepository.class), mock(FestivalTicketRepository.class), mock(UserRepository.class),
                mock(VenueRepository.class), mock(BookmarkRepository.class), mock(NotificationRepository.class),
                mock(PostRepository.class), mock(ArtistMatchService.class), mock(KopisVenueService.class), viewGuard);
    }

    @Test
    void sameViewerIsCountedOnce() {
        service.getFestival(1L, "ip1.1.1.1");
        service.getFestival(1L, "ip1.1.1.1");

        assertEquals(1, festival.getViewCount());
    }

    @Test
    void differentViewersAreCountedSeparately() {
        service.getFestival(1L, "ip1.1.1.1");
        service.getFestival(1L, "u7");

        assertEquals(2, festival.getViewCount());
    }

    @Test
    void botIsNotCounted() {
        service.getFestival(1L, ViewGuard.BOT_KEY);

        assertEquals(0, festival.getViewCount());
    }

    @Test
    void postAndFestivalWithSameIdDoNotBlockEachOther() {
        viewGuard.shouldCount(ViewGuard.Target.POST, 1L, "u7");

        service.getFestival(1L, "u7");

        assertEquals(1, festival.getViewCount());
    }
}
