package com.pickstage.backend.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// 같은 사람이 같은 글을 새로고침해도 조회수가 한 번만 오르도록, 최근에 본 (글, 조회자) 기록을 메모리에 둔다.
// 서버가 재시작되면 기록이 지워진다 (조회수 중복 방지 용도라 그 정도는 허용한다).
@Component
public class PostViewGuard {
    private static final long WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final int PRUNE_THRESHOLD = 20_000;

    private final ConcurrentHashMap<String, Long> lastViewedAt = new ConcurrentHashMap<>();

    // 이 조회자가 이 글을 WINDOW 안에 이미 봤으면 false, 처음이면 기록하고 true
    public boolean shouldCount(Long postId, String viewerKey) {
        if (viewerKey == null || viewerKey.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        boolean[] counted = { false };
        lastViewedAt.compute(postId + ":" + viewerKey, (key, previous) -> {
            if (previous != null && now - previous < WINDOW_MILLIS) {
                return previous;
            }
            counted[0] = true;
            return now;
        });
        if (lastViewedAt.size() > PRUNE_THRESHOLD) {
            lastViewedAt.values().removeIf(viewedAt -> now - viewedAt >= WINDOW_MILLIS);
        }
        return counted[0];
    }
}
