package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FestivalDraftLineupTest {

    @Test
    void 나열한_1차_라인업_문장을_이름_단위로_나눈다() {
        List<String> names = FestivalDraftService.splitLineupName(
                "1차 라인업: 10CM, 거니, 과카몰리, 로시, 선우정아, 이오욱(LOW), 카더가든, 퓨처피어클럽");
        assertEquals(List.of("10CM", "거니", "과카몰리", "로시", "선우정아", "이오욱(LOW)", "카더가든", "퓨처피어클럽"), names);
    }

    @Test
    void 줄바꿈_슬래시_글머리표로_나열해도_나눈다() {
        assertEquals(List.of("10CM", "거니", "로시"), FestivalDraftService.splitLineupName("10CM\n거니\n로시"));
        assertEquals(List.of("10CM", "거니", "로시"), FestivalDraftService.splitLineupName("10CM / 거니 / 로시"));
        assertEquals(List.of("10CM", "거니"), FestivalDraftService.splitLineupName("• 10CM\n• 거니"));
    }

    @Test
    void 이름_하나는_그대로_둔다() {
        assertEquals(List.of("이영지"), FestivalDraftService.splitLineupName("이영지"));
        assertEquals(List.of("AxMxP"), FestivalDraftService.splitLineupName("AxMxP"));
        assertEquals(List.of("AC/DC"), FestivalDraftService.splitLineupName("AC/DC"));
        assertEquals(List.of("이오욱(LOW)"), FestivalDraftService.splitLineupName("이오욱(LOW)"));
    }

    @Test
    void 쉼표가_하나뿐인_영문_이름은_한_팀으로_본다() {
        assertEquals(List.of("Earth, Wind & Fire"), FestivalDraftService.splitLineupName("Earth, Wind & Fire"));
    }

    @Test
    void 라벨과_군더더기_문구는_이름으로_넣지_않는다() {
        assertEquals(List.of("카더가든", "로시"), FestivalDraftService.splitLineupName("라인업: 카더가든, 로시, 등"));
        assertEquals(List.of("카더가든"), FestivalDraftService.splitLineupName("카더가든, TBA"));
        assertTrue(FestivalDraftService.splitLineupName("1차 라인업").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName("  ").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName(null).isEmpty());
    }

    @Test
    void 날짜_제목_줄은_이름으로_넣지_않는다() {
        assertTrue(FestivalDraftService.splitLineupName("12/18 (금)").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName("12/19 (토)").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName("2026.12.20 SAT").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName("DAY 1").isEmpty());
        assertEquals(List.of("HANABIE."), FestivalDraftService.splitLineupName("HANABIE.\n12/18 (금)"));
    }

    @Test
    void 장식_문자로_쓴_날짜_줄과_안내_문구도_걸러낸다() {
        assertTrue(FestivalDraftService.splitLineupName("𝟭𝟮.𝟯𝟭 𝙏𝙃𝙐").isEmpty());
        assertTrue(FestivalDraftService.splitLineupName("𝗧𝗕𝗔").isEmpty());
        assertEquals(List.of("실리카겔", "혁오"), FestivalDraftService.splitLineupName("𝟭𝟮.𝟯𝟭 𝙏𝙃𝙐\n실리카겔\n혁오"));
        assertEquals(List.of("𝗔𝗫𝗠𝗫𝗣"), FestivalDraftService.splitLineupName("𝗔𝗫𝗠𝗫𝗣"));
    }

    @Test
    void 인스타_계정과_끝_이모지는_이름에서_뗀다() {
        assertEquals(List.of("아시안체어샷"), FestivalDraftService.splitLineupName("아시안체어샷 (@asianchairshot) \uD83C\uDD95"));
        assertEquals(List.of("이오욱(LOW)"), FestivalDraftService.splitLineupName("이오욱(LOW)"));
    }
}
