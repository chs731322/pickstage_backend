package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.dto.WeatherSummary;
import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WeatherServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    // 예보 행 하나: {fcstDate, fcstTime, category, fcstValue}
    private String row(String date, String time, String category, String value) {
        return String.format("{\"fcstDate\":\"%s\",\"fcstTime\":\"%s\",\"category\":\"%s\",\"fcstValue\":\"%s\"}",
                date, time, category, value);
    }

    private JsonNode rows(String... rows) throws Exception {
        return mapper.readTree("[" + String.join(",", rows) + "]");
    }

    // ---- 기준 시각 계산 ----

    @Test
    void 단기예보는_02시10분_이후에는_오늘_02시_발표분을_쓴다() {
        assertArrayEquals(new String[] { "20261115", "0200" },
                WeatherService.shortTermBase(LocalDateTime.of(2026, 11, 15, 2, 10)));
        assertArrayEquals(new String[] { "20261115", "0200" },
                WeatherService.shortTermBase(LocalDateTime.of(2026, 11, 15, 22, 0)));
    }

    @Test
    void 단기예보는_02시10분_전에는_어제_23시_발표분을_쓴다() {
        assertArrayEquals(new String[] { "20261114", "2300" },
                WeatherService.shortTermBase(LocalDateTime.of(2026, 11, 15, 2, 9)));
        assertArrayEquals(new String[] { "20261114", "2300" },
                WeatherService.shortTermBase(LocalDateTime.of(2026, 11, 15, 0, 30)));
    }

    @Test
    void 중기예보_발표_시각은_30분_여유를_두고_06시_18시_중_가장_최근을_쓴다() {
        assertEquals("202611150600", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 7, 0)));
        assertEquals("202611150600", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 6, 30)));
        assertEquals("202611141800", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 6, 29)));
        assertEquals("202611141800", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 5, 0)));
        assertEquals("202611151800", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 18, 30)));
        assertEquals("202611150600", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 18, 29)));
        // 자정 직후 (시간이 -1이 되는 경우)
        assertEquals("202611141800", WeatherService.latestAnnouncementTime(LocalDateTime.of(2026, 11, 15, 0, 10)));
    }

    // ---- 하루 요약 ----

    @Test
    void 최저_최고_기온은_TMN_TMX를_우선_쓰고_반올림한다() throws Exception {
        JsonNode rows = rows(
                row("20261115", "0600", "TMP", "3"), row("20261115", "1500", "TMP", "12"),
                row("20261115", "0600", "TMN", "2.6"), row("20261115", "1500", "TMX", "11.4"));

        WeatherSummary.Day day = WeatherService.summarizeDay(rows, TODAY, 0);

        assertEquals(3, day.tempMin());
        assertEquals(11, day.tempMax());
    }

    @Test
    void TMN_TMX가_없으면_시간별_기온의_최저_최고를_쓴다() throws Exception {
        JsonNode rows = rows(
                row("20261115", "0600", "TMP", "4"), row("20261115", "1200", "TMP", "13"), row("20261115", "2100", "TMP", "7"));

        WeatherSummary.Day day = WeatherService.summarizeDay(rows, TODAY, 0);

        assertEquals(4, day.tempMin());
        assertEquals(13, day.tempMax());
    }

    @Test
    void 낮에_비가_오면_비를_보여주고_아니면_가장_흐린_하늘을_보여준다() throws Exception {
        JsonNode rainy = rows(
                row("20261115", "0900", "TMP", "5"), row("20261115", "1200", "SKY", "1"),
                row("20261115", "1500", "SKY", "4"), row("20261115", "1500", "PTY", "1"));
        JsonNode cloudy = rows(
                row("20261115", "0900", "TMP", "5"), row("20261115", "1200", "SKY", "1"), row("20261115", "1500", "SKY", "3"));

        WeatherSummary.Day rain = WeatherService.summarizeDay(rainy, TODAY, 0);
        WeatherSummary.Day cloud = WeatherService.summarizeDay(cloudy, TODAY, 0);

        assertEquals("비", rain.sky());
        assertTrue(rain.isRain());
        assertEquals("구름많음", cloud.sky());
        assertFalse(cloud.isRain());
    }

    @Test
    void 낮_시간_밖의_하늘과_강수는_보지_않는다() throws Exception {
        JsonNode rows = rows(
                row("20261115", "0300", "TMP", "5"), row("20261115", "0300", "SKY", "4"), row("20261115", "0300", "PTY", "3"),
                row("20261115", "2100", "SKY", "4"), row("20261115", "1200", "SKY", "1"));

        WeatherSummary.Day day = WeatherService.summarizeDay(rows, TODAY, 0);

        assertEquals("맑음", day.sky());
        assertFalse(day.isRain());
    }

    @Test
    void 기온을_알_수_없으면_그_날은_건너뛴다() throws Exception {
        assertNull(WeatherService.summarizeDay(rows(row("20261116", "0900", "TMP", "5")), TODAY, 0));
        assertNull(WeatherService.summarizeDay(rows(row("20261115", "0900", "SKY", "1")), TODAY, 0));
    }

    // ---- 단기예보 전체 ----

    @Test
    void 단기예보는_오늘과_이어지는_이틀을_담는다() throws Exception {
        JsonNode rows = rows(
                row("20261115", "0600", "TMP", "3"), row("20261115", "1500", "TMP", "12"),
                row("20261116", "0600", "TMP", "4"), row("20261116", "1500", "TMP", "14"),
                row("20261117", "0600", "TMP", "5"), row("20261117", "1500", "TMP", "15"));

        WeatherSummary summary = WeatherService.buildShortTermSummary(rows, TODAY);

        assertEquals("short", summary.source());
        assertEquals(TODAY, summary.date());
        assertEquals(0, summary.dayOffset());
        assertEquals(2, summary.upcoming().size());
        assertEquals(1, summary.upcoming().get(0).dayOffset());
        assertEquals(LocalDate.of(2026, 11, 17), summary.upcoming().get(1).date());
    }

    @Test
    void 예보가_없는_날은_빠지고_오늘이_없으면_실패한다() throws Exception {
        JsonNode onlyToday = rows(row("20261115", "0600", "TMP", "3"), row("20261115", "1500", "TMP", "12"));
        assertEquals(0, WeatherService.buildShortTermSummary(onlyToday, TODAY).upcoming().size());

        JsonNode noToday = rows(row("20261116", "0600", "TMP", "3"), row("20261116", "1500", "TMP", "12"));
        assertThrows(IllegalStateException.class, () -> WeatherService.buildShortTermSummary(noToday, TODAY));
    }

    // ---- 중기예보 ----

    @Test
    void 중기예보는_값이_있는_가장_가까운_날을_고르고_하늘은_오전_오후_순으로_쓴다() throws Exception {
        JsonNode ta = mapper.readTree("{\"taMin4\":1,\"taMax4\":9,\"taMin5\":2,\"taMax5\":10}");
        JsonNode land = mapper.readTree("{\"wf4Am\":\"\",\"wf4Pm\":\"구름많음\",\"wf5Am\":\"맑음\"}");

        WeatherSummary summary = WeatherService.buildMidTermSummary(ta, land, TODAY);

        assertEquals("mid", summary.source());
        assertEquals(4, summary.dayOffset());
        assertEquals(LocalDate.of(2026, 11, 19), summary.date());
        assertEquals(1, summary.tempMin());
        assertEquals(9, summary.tempMax());
        assertEquals("구름많음", summary.sky());
        assertTrue(summary.upcoming().isEmpty());
    }

    @Test
    void 중기예보에_값이_하나도_없으면_실패한다() throws Exception {
        JsonNode empty = mapper.readTree("{}");
        assertThrows(IllegalStateException.class, () -> WeatherService.buildMidTermSummary(empty, empty, TODAY));
    }

    // ---- 인증키 ----

    @Test
    void 이미_인코딩된_키는_그대로_쓰고_인코딩되지_않은_키는_인코딩한다() {
        WeatherService service = new WeatherService(mapper);
        service.setServiceKeyForTest("abc%2Bdef%3D%3D");
        assertEquals("abc%2Bdef%3D%3D", service.encodedKey());
        service.setServiceKeyForTest("abc+def==");
        assertEquals("abc%2Bdef%3D%3D", service.encodedKey());
    }

    @Test
    void 키가_설정되지_않으면_안내_문구와_함께_실패한다() {
        WeatherService service = new WeatherService(mapper);

        ExternalServiceUnavailableException e = assertThrows(ExternalServiceUnavailableException.class, service::getSummary);

        assertTrue(e.getMessage().contains("KMA_API_KEY"));
    }

    // ---- 보관(캐시)과 실패 대응 ----

    // 시계를 직접 움직일 수 있고, 기상청 대신 정해둔 결과(또는 실패)를 돌려주는 테스트용 서비스
    private static class Fake extends WeatherService {
        final List<Object> queue = new ArrayList<>();
        int calls = 0;

        Fake(ObjectMapper mapper, MutableClock clock) {
            super(mapper, clock);
            setServiceKeyForTest("key");
        }

        @Override
        WeatherSummary loadSummary() {
            calls++;
            Object next = queue.remove(0);
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (WeatherSummary) next;
        }
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-15T03:00:00Z");

        @Override public ZoneId getZone() { return WeatherService.SEOUL; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private WeatherSummary summary(int max) {
        return new WeatherSummary(TODAY, 0, 1, max, "맑음", false, "short", List.of());
    }

    private ExternalServiceUnavailableException fail() {
        return new ExternalServiceUnavailableException("실패");
    }

    @Test
    void 삼십분_안에는_기상청을_다시_부르지_않고_지나면_새로_가져온다() {
        MutableClock clock = new MutableClock();
        Fake service = new Fake(mapper, clock);
        WeatherSummary first = summary(10);
        service.queue.add(first);
        service.queue.add(summary(11));

        assertSame(first, service.getSummary());
        clock.advance(Duration.ofMinutes(29));
        assertSame(first, service.getSummary());
        assertEquals(1, service.calls);

        clock.advance(Duration.ofMinutes(2));
        assertEquals(11, service.getSummary().tempMax());
        assertEquals(2, service.calls);
    }

    @Test
    void 새로_가져오지_못하면_6시간_안의_옛_결과를_대신_보여준다() {
        MutableClock clock = new MutableClock();
        Fake service = new Fake(mapper, clock);
        WeatherSummary first = summary(10);
        service.queue.add(first);
        service.queue.add(fail());

        service.getSummary();
        clock.advance(Duration.ofMinutes(31));

        assertSame(first, service.getSummary());
    }

    @Test
    void 옛_결과가_6시간을_넘으면_오류를_돌려준다() {
        MutableClock clock = new MutableClock();
        Fake service = new Fake(mapper, clock);
        service.queue.add(summary(10));
        service.queue.add(fail());

        service.getSummary();
        clock.advance(Duration.ofHours(6).plusMinutes(1));

        assertThrows(ExternalServiceUnavailableException.class, service::getSummary);
    }

    @Test
    void 처음부터_실패하면_오류를_돌려주고_1분_동안은_다시_부르지_않는다() {
        MutableClock clock = new MutableClock();
        Fake service = new Fake(mapper, clock);
        service.queue.add(fail());
        service.queue.add(summary(10));

        assertThrows(ExternalServiceUnavailableException.class, service::getSummary);
        clock.advance(Duration.ofSeconds(30));
        assertThrows(ExternalServiceUnavailableException.class, service::getSummary);
        assertEquals(1, service.calls);

        clock.advance(Duration.ofSeconds(31));
        assertEquals(10, service.getSummary().tempMax());
        assertEquals(2, service.calls);
    }

    // 실패 원인은 로그에 남기되, 인증키가 들어 있는 주소는 남기지 않는다
    @org.junit.jupiter.api.Test
    void rootCauseShowsNetworkReasonWithoutUrl() {
        RuntimeException timeout = new RuntimeException("GET https://example/?serviceKey=SECRET",
                new java.net.SocketTimeoutException("Connect timed out"));
        RuntimeException other = new RuntimeException("serviceKey=SECRET", new IllegalArgumentException("serviceKey=SECRET"));

        org.junit.jupiter.api.Assertions.assertEquals("SocketTimeoutException (Connect timed out)",
                WeatherService.rootCause(timeout));
        org.junit.jupiter.api.Assertions.assertEquals("IllegalArgumentException", WeatherService.rootCause(other));
    }
}
