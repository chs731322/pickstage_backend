package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.pickstage.backend.dto.ArtistSearchResponse;
import com.pickstage.backend.repository.ArtistRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.InterestArtistRepository;

// 아티스트 목록: 예정 공연이 있는 아티스트를 이름순으로 돌려준다
class ArtistServiceListTest {

    @Test
    void 예정_공연이_있는_아티스트를_이름순으로_돌려준다() {
        ArtistRepository repository = mock(ArtistRepository.class);
        ArtistService service = new ArtistService(repository, mock(FestivalArtistRepository.class), mock(InterestArtistRepository.class), mock(ObjectMapper.class));
        when(repository.findWithUpcomingFestivals(any(), any())).thenReturn(List.of(
                new Object[] { 2L, "혁오", "HYUKOH", "https://img/2.jpg", 3L },
                new Object[] { 1L, "국카스텐", "Guckkasten", null, 1L }));

        List<ArtistSearchResponse> result = service.listUpcomingArtists();

        assertEquals(2, result.size());
        assertEquals("국카스텐", result.get(0).getName());
        assertEquals(1, result.get(0).getUpcomingCount());
        assertEquals("혁오", result.get(1).getName());
        assertEquals(3, result.get(1).getUpcomingCount());
    }
}
