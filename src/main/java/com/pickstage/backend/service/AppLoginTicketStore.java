package com.pickstage.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pickstage.backend.dto.AuthResponse;

// 앱 소셜 로그인: 서버가 로그인을 끝낸 뒤 앱 주소에 토큰을 직접 싣지 않고, 1회용 티켓만 돌려준다.
// 앱은 이 티켓으로 토큰을 한 번만 받아 갈 수 있다 (60초 안에, 한 번만).
@Component
public class AppLoginTicketStore {

    static final long TTL_SECONDS = 60;
    private static final int MAX_TICKETS = 10_000;

    private record Entry(AuthResponse response, Instant expiresAt) {
    }

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public AppLoginTicketStore() {
        this(Clock.systemUTC());
    }

    // 테스트용
    AppLoginTicketStore(Clock clock) {
        this.clock = clock;
    }

    public String issue(AuthResponse response) {
        purgeExpired();
        if (tickets.size() >= MAX_TICKETS) {
            throw new IllegalStateException("잠시 후 다시 시도해주세요.");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(response, clock.instant().plusSeconds(TTL_SECONDS)));
        return ticket;
    }

    // 티켓을 꺼내면 바로 사라진다 (두 번 쓸 수 없다)
    public Optional<AuthResponse> redeem(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
