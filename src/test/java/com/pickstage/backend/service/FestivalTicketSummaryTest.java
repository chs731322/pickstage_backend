package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.entity.Festival;

class FestivalTicketSummaryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0);
    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final LocalDateTime OFFICIAL = LocalDateTime.of(2026, 12, 2, 12, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 12, 20, 12, 0);

    @Test
    void 앞으로_열릴_가장_이른_시각을_대표로_하고_빈_예매_링크를_채운다() {
        Festival festival = Festival.builder().build();

        FestivalTicketSummary.apply(festival, Arrays.asList(LATER, null, EARLY, OFFICIAL), Arrays.asList(null, "https://a"), NOW);

        assertEquals(OFFICIAL, festival.getTicketOpenDate());
        assertEquals("https://a", festival.getBookingUrl());
    }

    @Test
    void 모두_지났으면_첫_오픈_시각이고_시각이_없으면_원래_값을_둔다() {
        Festival past = Festival.builder().build();
        FestivalTicketSummary.apply(past, List.of(EARLY), List.of(), NOW);
        assertEquals(EARLY, past.getTicketOpenDate());

        Festival kept = Festival.builder().ticketOpenDate(OFFICIAL).bookingUrl("https://old").build();
        FestivalTicketSummary.apply(kept, List.of(), List.of("https://new"), NOW);
        assertEquals(OFFICIAL, kept.getTicketOpenDate());
        assertEquals("https://old", kept.getBookingUrl());
    }

    @Test
    void 티켓을_더할_때는_더_가까운_오픈만_바꾼다() {
        Festival festival = Festival.builder().ticketOpenDate(EARLY).build();

        FestivalTicketSummary.add(festival, LATER, null, NOW); // 지금 값이 지났으니 바꾼다
        FestivalTicketSummary.add(festival, OFFICIAL, null, NOW); // 더 가까워서 바꾼다
        FestivalTicketSummary.add(festival, LATER, null, NOW); // 더 늦어서 그대로

        assertEquals(OFFICIAL, festival.getTicketOpenDate());
    }
}
