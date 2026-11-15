package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// 실제 단어 목록(profanity.txt)으로 검사한다
class ProfanityFilterTest {

    private final ProfanityFilter filter = new ProfanityFilter();

    @Test
    void 욕설이_들어_있으면_걸린다() {
        assertTrue(filter.containsProfanity("이 공연 진짜 시발 별로였음"));
        assertTrue(filter.containsProfanity("병신같은 운영"));
        assertTrue(filter.containsProfanity("ㅅㅂ 뭐하냐"));
    }

    @Test
    void 글자_사이에_기호를_끼워도_걸린다() {
        assertTrue(filter.containsProfanity("시.발"));
        assertTrue(filter.containsProfanity("씨_발 진짜"));
        assertTrue(filter.containsProfanity("개*새*끼"));
    }

    @Test
    void 혐오_표현과_불법_스팸도_걸린다() {
        assertTrue(filter.containsProfanity("한남충들 오지마"));
        assertTrue(filter.containsProfanity("토토사이트 추천합니다"));
    }

    @Test
    void 정상적인_후기와_일상_표현은_걸리지_않는다() {
        assertFalse(filter.containsProfanity("스탠딩 B구역 시야가 좋았어요. 다음에 또 가고 싶어요!"));
        assertFalse(filter.containsProfanity("존나 좋았다는 표현은 이 목록에 없어서 통과"));
        assertFalse(filter.containsProfanity("공연 끝나고 이시 발표를 봤어요")); // 띄어쓰기를 지우면 걸리던 문장
        assertFalse(filter.containsProfanity("락페 티켓 오픈 알림 받고 싶어요"));
    }

    @Test
    void 금지_단어가_들어_있어도_정상인_말은_허용한다() {
        assertFalse(filter.containsProfanity("지하철 시발점에서 만나요"));
        assertFalse(filter.containsProfanity("시발역 근처 공연장"));
        assertFalse(filter.containsProfanity("병신년 새해 공연"));
        assertFalse(filter.containsProfanity("시바견 굿즈 교환해요"));
        // 허용 단어를 쓰더라도 같은 글에 욕설이 따로 있으면 걸린다
        assertTrue(filter.containsProfanity("시발점이 어디야 시발"));
    }

    @Test
    void 비어_있거나_null이면_통과한다() {
        assertFalse(filter.containsProfanity(null));
        assertFalse(filter.containsProfanity(""));
        assertFalse(filter.containsProfanity("   "));
    }

    @Test
    void assertClean은_하나라도_걸리면_안내_문구로_거절한다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> filter.assertClean("정상 제목", null, "시발 내용"));
        assertEquals(ProfanityFilter.MESSAGE, e.getMessage());
        assertDoesNotThrow(() -> filter.assertClean("정상 제목", null, "정상 내용"));
    }
}
