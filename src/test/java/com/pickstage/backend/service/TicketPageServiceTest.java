package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.dto.FestivalDraftResponse.Draft;
import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;

class TicketPageServiceTest {

    @Test
    void 허용된_예매처_주소만_뽑고_중복과_끝_문장부호는_정리한다() {
        String text = "예매: https://nol.yanolja.com/ticket/products/26015181. 공식: https://example.com/a "
                + "멜론 https://ticket.melon.com/performance/index.htm?prodId=1 (https://nol.yanolja.com/ticket/products/26015181)";
        assertEquals(List.of("https://nol.yanolja.com/ticket/products/26015181",
                "https://ticket.melon.com/performance/index.htm?prodId=1"), TicketPageService.extractTicketUrls(text));
    }

    @Test
    void 위험한_주소는_열지_않는다() {
        assertFalse(TicketPageService.isAllowedTicketUrl("http://nol.yanolja.com/x"));              // http
        assertFalse(TicketPageService.isAllowedTicketUrl("https://nol.yanolja.com.evil.com/x"));    // 비슷한 도메인
        assertFalse(TicketPageService.isAllowedTicketUrl("https://evil.com/nol.yanolja.com"));
        assertFalse(TicketPageService.isAllowedTicketUrl("https://user@nol.yanolja.com/x"));        // 계정 정보
        assertFalse(TicketPageService.isAllowedTicketUrl("https://nol.yanolja.com:8443/x"));        // 다른 포트
        assertFalse(TicketPageService.isAllowedTicketUrl("https://localhost/x"));
        assertFalse(TicketPageService.isAllowedTicketUrl("https://169.254.169.254/latest/meta-data"));
        assertTrue(TicketPageService.isAllowedTicketUrl("https://tickets.interpark.com/goods/1"));
        assertTrue(TicketPageService.isAllowedTicketUrl("https://www.ticketlink.co.kr/product/65770"));
    }

    @Test
    void 주소에서_예매처_이름을_알아낸다() {
        assertEquals("NOL티켓", TicketPageService.vendorNameOf("https://nol.yanolja.com/ticket/products/1"));
        assertEquals("멜론티켓", TicketPageService.vendorNameOf("https://ticket.melon.com/performance/index.htm?prodId=1"));
        assertEquals("티켓링크", TicketPageService.vendorNameOf("https://www.ticketlink.co.kr/product/1"));
        assertNull(TicketPageService.vendorNameOf("https://example.com/x"));
    }

    @Test
    void HTML에서_스크립트를_빼고_글자만_남긴다() {
        String html = "<html><head><style>.a{}</style><script>var x='1일권';</script></head><body>"
                + "<h1>썸띵 뉴 뮤직 페스티벌</h1><ul><li>1일권 &nbsp; 110,000원</li><li>2일권 160,000원</li></ul><!-- 숨김 --></body></html>";
        String text = TicketPageService.htmlToText(html);
        assertTrue(text.contains("썸띵 뉴 뮤직 페스티벌"));
        assertTrue(text.contains("1일권 110,000원"));
        assertTrue(text.contains("2일권 160,000원"));
        assertFalse(text.contains("var x"));
        assertFalse(text.contains("숨김"));
    }

    private static Draft draftWith(List<TicketDraft> tickets) {
        return Draft.builder().title("t").tickets(tickets).lineup(new ArrayList<>()).build();
    }

    @Test
    void 권종이_비어도_가격이나_링크가_있으면_일반으로_살린다() {
        TicketDraft raw = TicketDraft.builder().label(null).price(110000).vendorName(null)
                .bookingUrl("https://nol.yanolja.com/ticket/products/26015181").build();
        Draft fixed = FestivalDraftService.withTicketFixes(draftWith(List.of(raw)), List.of(), new ArrayList<>());
        assertEquals("일반", fixed.getTickets().get(0).getLabel());
        assertEquals("NOL티켓", fixed.getTickets().get(0).getVendorName());
        assertEquals(110000, fixed.getTickets().get(0).getPrice());
    }

    @Test
    void 티켓을_못_찾아도_예매처_페이지가_있으면_링크만_담고_알려준다() {
        TicketPageService.TicketPage page = TicketPageService.TicketPage.builder()
                .url("https://nol.yanolja.com/ticket/products/26015181").vendorName("NOL티켓").text("...").build();
        List<String> warnings = new ArrayList<>();
        Draft fixed = FestivalDraftService.withTicketFixes(draftWith(new ArrayList<>()), List.of(page), warnings);
        assertEquals(1, fixed.getTickets().size());
        assertEquals("https://nol.yanolja.com/ticket/products/26015181", fixed.getTickets().get(0).getBookingUrl());
        assertEquals(1, warnings.size());
    }

    @Test
    void 예매처_페이지가_하나뿐이면_링크가_빈_티켓에_채운다() {
        TicketPageService.TicketPage page = TicketPageService.TicketPage.builder()
                .url("https://nol.yanolja.com/ticket/products/26015181").vendorName("NOL티켓").text("...").build();
        TicketDraft raw = TicketDraft.builder().label("1일권").price(110000).build();
        Draft fixed = FestivalDraftService.withTicketFixes(draftWith(List.of(raw)), List.of(page), new ArrayList<>());
        assertEquals("https://nol.yanolja.com/ticket/products/26015181", fixed.getTickets().get(0).getBookingUrl());
        assertEquals("NOL티켓", fixed.getTickets().get(0).getVendorName());
    }
}
