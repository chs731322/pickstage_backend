package com.pickstage.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.service.FestivalUpdateSuggestionService;

class AdminFestivalUpdateControllerTest {

    private final FestivalUpdateSuggestionService service = mock(FestivalUpdateSuggestionService.class);
    private final AdminFestivalUpdateController controller = new AdminFestivalUpdateController(service);

    @Test
    void 승인_API는_승인만_한다() {
        controller.approve(1L, 5L);

        verify(service).approve(1L, 5L);
        verify(service, never()).reject(any(), any());
    }

    @Test
    void 거절_API는_거절만_한다() {
        controller.reject(1L, 5L);

        verify(service).reject(1L, 5L);
        verify(service, never()).approve(any(), any());
    }
}
