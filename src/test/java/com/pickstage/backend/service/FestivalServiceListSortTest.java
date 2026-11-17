package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

// 공연 목록 정렬: 시작일·찜 수가 같은 공연이 많아도 페이지를 넘길 때 같은 공연이 두 번 나오거나 빠지지 않도록
// 어떤 정렬이든 맨 끝에 id 순서가 붙는지 확인한다.
class FestivalServiceListSortTest {

    private FestivalRepository festivalRepository;
    private FestivalService service;

    @BeforeEach
    void setUp() {
        festivalRepository = mock(FestivalRepository.class);
        service = new FestivalService(festivalRepository, mock(FestivalImageRepository.class),
                mock(FestivalArtistRepository.class), mock(FestivalTicketRepository.class), mock(UserRepository.class),
                mock(VenueRepository.class), mock(BookmarkRepository.class), mock(NotificationRepository.class),
                mock(PostRepository.class), mock(ArtistMatchService.class), mock(KopisVenueService.class));
        when(festivalRepository.findByFilters(any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any())).thenReturn(new PageImpl<Festival>(List.of()));
    }

    // 목록을 한 번 조회하고, 저장소로 넘어간 정렬 조건을 돌려준다
    private Sort sortUsedFor(String sort) {
        service.getFestivals(null, null, null, sort, false, false, null, null, 0, 20);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(festivalRepository).findByFilters(any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), pageable.capture());
        return pageable.getValue().getSort();
    }

    @ParameterizedTest
    @ValueSource(strings = { "latest", "popular", "start", "ticket_open", "ticket_open_recent", "알수없는정렬" })
    void 어떤_정렬이든_맨_끝에_id_순서가_붙는다(String sort) {
        List<Sort.Order> orders = sortUsedFor(sort).toList();

        Sort.Order last = orders.get(orders.size() - 1);
        assertEquals("id", last.getProperty());
        assertEquals(Sort.Direction.ASC, last.getDirection());
        // id 만 덩그러니 있으면 원래 정렬이 사라진 것이므로, 앞에 다른 정렬 기준이 남아 있어야 한다
        assertTrue(orders.size() >= 2, "원래 정렬 기준이 있어야 한다: " + orders);
    }

    @Test
    void 정렬을_주지_않으면_최신_등록순에_id_순서가_붙는다() {
        List<Sort.Order> orders = sortUsedFor(null).toList();

        assertEquals("createdAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("id", orders.get(orders.size() - 1).getProperty());
    }

    @Test
    void 임박순은_시작일_정렬_뒤에_id_순서가_붙는다() {
        List<Sort.Order> orders = sortUsedFor("start").toList();

        // 진행 중/예정/종료 구분 -> 예정 공연 시작일 -> 종료 공연 시작일 -> id
        assertEquals(4, orders.size(), orders.toString());
        assertEquals("id", orders.get(3).getProperty());
    }

    @Test
    void 인기순은_끝난_공연을_뒤로_보낸_뒤_찜_조회수_id_순이다() {
        List<String> properties = sortUsedFor("popular").toList().stream().map(Sort.Order::getProperty).toList();

        assertEquals(4, properties.size(), properties.toString());
        assertEquals(List.of("bookmarkCount", "viewCount", "id"), properties.subList(1, 4));
    }
}
