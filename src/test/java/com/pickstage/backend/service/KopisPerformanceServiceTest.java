package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class KopisPerformanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 26);

    @Test
    void 목록_응답을_읽고_이름과_기간이_맞는_공연만_고른다() throws Exception {
        var list = KopisPerformanceService.parseList("""
                <dbs>
                  <db><mt20id>PF1</mt20id><prfnm>다른 콘서트</prfnm><prfpdfrom>2026.12.26</prfpdfrom><prfpdto>2026.12.26</prfpdto><fcltynm>홀</fcltynm></db>
                  <db><mt20id>PF2</mt20id><prfnm>썸띵 뉴 뮤직 페스티벌 [수원]</prfnm><prfpdfrom>2026.12.26</prfpdfrom><prfpdto>2026.12.27</prfpdto><fcltynm>홀</fcltynm></db>
                </dbs>
                """);

        var match = KopisPerformanceService.pickMatch("썸띵 뉴 뮤직 페스티벌 2026", DAY, DAY.plusDays(1), list);

        assertEquals("PF2", match.orElseThrow().id());
        assertTrue(KopisPerformanceService.pickMatch("썸띵 뉴 뮤직 페스티벌 2026", DAY.plusMonths(2), DAY.plusMonths(2), list).isEmpty());
    }

    @Test
    void 상세_응답에서_출연진과_가격과_예매처를_읽는다() throws Exception {
        var detail = KopisPerformanceService.parseDetail("""
                <dbs><db>
                  <mt20id>PF2</mt20id><prfnm>썸띵 뉴</prfnm>
                  <prfcast>혁오, 터치드, 윤미래 등</prfcast>
                  <pcseguidance>1일권 110,000원, 2일권 160,000원</pcseguidance>
                  <relates>
                    <relate><relatenm>놀티켓</relatenm><relateurl>https://nol.yanolja.com/ticket/products/1</relateurl></relate>
                    <relate><relatenm>공식 홈페이지</relatenm><relateurl>https://example.com</relateurl></relate>
                  </relates>
                </db></dbs>
                """).orElseThrow();

        assertEquals(List.of("혁오", "터치드", "윤미래"), detail.cast());
        assertEquals(List.of(new KopisPerformanceService.Price("1일권", 110000), new KopisPerformanceService.Price("2일권", 160000)), detail.prices());
        var tickets = FestivalUpdateScanner.toTickets(detail);
        assertEquals(2, tickets.size());
        assertEquals("NOL티켓", tickets.get(0).getVendorName());
        assertEquals("https://nol.yanolja.com/ticket/products/1", tickets.get(1).getBookingUrl());
    }

    @Test
    void 검색어는_괄호와_연도를_빼고_점점_짧게() {
        assertEquals(List.of("데이브레이크 루시 산보 연말콘서트", "데이브레이크 루시", "데이브레이크"),
                KopisPerformanceService.searchKeywords("2026 데이브레이크 루시 산보 연말콘서트 〈WINTER CITY〉"));
    }
}
