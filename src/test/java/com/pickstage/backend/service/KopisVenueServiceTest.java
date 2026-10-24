package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.service.KopisVenueService.Facility;

class KopisVenueServiceTest {

    // 실제 KOPIS 응답과 같은 모양 ("킨텍스" 검색: 첫 번째가 엉뚱한 시설)
    private static final String LIST_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><dbs>"
            + "<db><mt10id>FC001</mt10id><fcltynm>뽀로로파크 [킨텍스]</fcltynm><sidonm>경기</sidonm><gugunnm>고양시</gugunnm></db>"
            + "<db><mt10id>FC002</mt10id><fcltynm>킨텍스</fcltynm><sidonm>경기</sidonm><gugunnm>고양시</gugunnm></db>"
            + "<db><mt10id>FC003</mt10id><fcltynm>현대백화점 문화홀 [킨텍스]</fcltynm><sidonm>경기</sidonm><gugunnm>고양시</gugunnm></db>"
            + "</dbs>";

    @Test
    void 검색어는_이름_끝_단어를_하나씩_줄여_최대_네_개() {
        assertEquals(List.of("킨텍스 제2전시장 7·8홀", "킨텍스 제2전시장", "킨텍스"),
                KopisVenueService.searchKeywords("킨텍스 제2전시장 7·8홀"));
        assertEquals(List.of("수원컨벤션센터"), KopisVenueService.searchKeywords("수원컨벤션센터 (경기 수원)"));
    }

    @Test
    void 이름이_정확히_같은_시설을_첫_결과보다_우선한다() throws Exception {
        List<Facility> found = KopisVenueService.parseFacilities(LIST_XML);
        assertEquals(3, found.size());
        Optional<Facility> best = KopisVenueService.pickBest("킨텍스", found);
        assertEquals("FC002", best.orElseThrow().getId());
        assertEquals("경기", best.get().getRegion());
    }

    @Test
    void 이름이_겹치지_않으면_고르지_않는다() throws Exception {
        List<Facility> found = KopisVenueService.parseFacilities(LIST_XML);
        assertTrue(KopisVenueService.pickBest("올림픽홀", found).isEmpty());
    }

    @Test
    void 정확히_같은_이름이_없으면_이름이_가장_가까운_포함_관계를_고른다() throws Exception {
        List<Facility> found = KopisVenueService.parseFacilities(
                "<dbs><db><mt10id>A</mt10id><fcltynm>수원컨벤션센터 컨벤션홀</fcltynm><sidonm>경기</sidonm></db>"
                        + "<db><mt10id>B</mt10id><fcltynm>수원컨벤션센터</fcltynm><sidonm>경기</sidonm></db></dbs>");
        // 정확히 같은 이름이 있으면 그것
        assertEquals("B", KopisVenueService.pickBest("수원컨벤션센터", found).orElseThrow().getId());
        // 없으면 포함 관계 중 길이가 가장 비슷한 것
        assertEquals("A", KopisVenueService.pickBest("수원컨벤션센터 컨벤션홀 1층", found).orElseThrow().getId());
    }

    @Test
    void 빈_응답이나_외부_개체가_들어간_XML도_안전하게_다룬다() throws Exception {
        assertTrue(KopisVenueService.parseFacilities("<dbs></dbs>").isEmpty());
        assertTrue(KopisVenueService.parseFacilities(null).isEmpty());
        boolean rejected = false;
        try {
            KopisVenueService.parseFacilities("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><dbs>&e;</dbs>");
        } catch (Exception expected) {
            rejected = true;
        }
        assertTrue(rejected, "DOCTYPE이 들어간 XML은 거부해야 한다");
    }
}
