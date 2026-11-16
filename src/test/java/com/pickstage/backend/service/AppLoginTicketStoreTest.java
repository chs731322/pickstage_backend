package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.dto.AuthResponse;

// 앱 소셜 로그인 티켓: 한 번만, 60초 안에만 쓸 수 있다
class AppLoginTicketStoreTest {

    private final AuthResponse response = AuthResponse.builder().accessToken("a").refreshToken("r").build();

    @Test
    void 티켓은_한_번만_쓸_수_있다() {
        AppLoginTicketStore store = new AppLoginTicketStore();
        String ticket = store.issue(response);

        assertEquals("a", store.redeem(ticket).orElseThrow().getAccessToken());
        assertTrue(store.redeem(ticket).isEmpty());
    }

    @Test
    void 모르는_티켓은_거절한다() {
        AppLoginTicketStore store = new AppLoginTicketStore();
        assertTrue(store.redeem("nope").isEmpty());
        assertTrue(store.redeem(null).isEmpty());
    }

    @Test
    void 만료된_티켓은_거절한다() {
        MutableClock clock = new MutableClock();
        AppLoginTicketStore store = new AppLoginTicketStore(clock);
        String ticket = store.issue(response);

        clock.advanceSeconds(AppLoginTicketStore.TTL_SECONDS + 1);

        assertTrue(store.redeem(ticket).isEmpty());
    }

    @Test
    void 티켓마다_값이_다르다() {
        AppLoginTicketStore store = new AppLoginTicketStore();
        assertTrue(!store.issue(response).equals(store.issue(response)));
    }

    // 시간을 마음대로 흘려보내는 시계
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T00:00:00Z");

        void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
