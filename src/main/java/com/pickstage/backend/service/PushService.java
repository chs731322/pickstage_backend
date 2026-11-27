package com.pickstage.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.pickstage.backend.dto.PushTokenResponse;
import com.pickstage.backend.entity.Notification;
import com.pickstage.backend.entity.PushToken;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.NotificationRepository;
import com.pickstage.backend.repository.PushTokenRepository;

// 앱 푸시 알림: 기기 토큰을 관리하고, 서비스 알림(티켓 오픈·공연 당일·사용자 지정·댓글)을 푸시로도 보낸다.
// 앱 안의 알림 목록이 기준이고 푸시는 "알려주는 수단"이라서, 푸시가 실패해도 알림 자체는 영향을 받지 않는다 (실패는 로그만 남긴다).
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    // 푸시 서버에 한 번에 보낼 수 있는 최대 개수
    static final int CHUNK_SIZE = 100;
    // 한 계정에 등록해 둘 수 있는 기기 수 (넘으면 가장 오래 안 쓴 기기부터 지운다)
    static final int MAX_DEVICES_PER_USER = 10;
    // 테스트 알림은 계정당 이 간격으로만 보낼 수 있다
    static final Duration TEST_COOLDOWN = Duration.ofSeconds(60);

    // Expo 푸시 토큰: ExponentPushToken[...] (예전 형식 ExpoPushToken[...] 도 받는다)
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{8,}\\]$");
    private static final Set<String> PLATFORMS = Set.of("ios", "android");

    private final PushTokenRepository pushTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ExpoPushClient expoPushClient;
    private final Executor executor;
    private final Clock clock;
    private final Map<Long, Instant> lastTestAt = new ConcurrentHashMap<>();

    @Autowired
    public PushService(PushTokenRepository pushTokenRepository, NotificationRepository notificationRepository,
            ExpoPushClient expoPushClient) {
        this(pushTokenRepository, notificationRepository, expoPushClient, ForkJoinPool.commonPool(), Clock.systemUTC());
    }

    // 테스트에서 실행기(바로 실행)와 시계를 바꿀 때 쓴다
    PushService(PushTokenRepository pushTokenRepository, NotificationRepository notificationRepository,
            ExpoPushClient expoPushClient, Executor executor, Clock clock) {
        this.pushTokenRepository = pushTokenRepository;
        this.notificationRepository = notificationRepository;
        this.expoPushClient = expoPushClient;
        this.executor = executor;
        this.clock = clock;
    }

    // ===================== 기기 토큰 관리 =====================

    // 앱이 켜질 때(로그인 상태) 토큰을 등록한다. 이미 있는 토큰이면 갱신하고, 다른 계정 것이었으면 지금 계정으로 옮긴다.
    @Transactional
    public PushTokenResponse register(Long userId, String token, String platform) {
        String cleanToken = validateToken(token);
        String cleanPlatform = validatePlatform(platform);

        PushToken saved = pushTokenRepository.findByToken(cleanToken).map(existing -> {
            if (existing.getUserId().equals(userId)) {
                existing.touch(cleanPlatform);
            } else {
                existing.reassignTo(userId, cleanPlatform);
            }
            return pushTokenRepository.save(existing);
        }).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return pushTokenRepository.save(PushToken.builder()
                    .userId(userId)
                    .token(cleanToken)
                    .platform(cleanPlatform)
                    .enabled(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });

        trimOldDevices(userId);
        return new PushTokenResponse(saved.isEnabled());
    }

    // 로그아웃할 때 이 기기의 토큰을 지운다 (내 토큰이 아니거나 없으면 아무 일도 하지 않는다)
    @Transactional
    public void unregister(Long userId, String token) {
        pushTokenRepository.findByToken(token == null ? "" : token.trim())
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(pushTokenRepository::delete);
    }

    // 이 기기로 푸시를 받을지 켜고 끈다
    @Transactional
    public PushTokenResponse setEnabled(Long userId, String token, boolean enabled) {
        PushToken pushToken = pushTokenRepository.findByToken(token == null ? "" : token.trim())
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalStateException("등록되지 않은 기기입니다. 앱을 다시 열어주세요."));
        pushToken.setEnabled(enabled);
        pushTokenRepository.save(pushToken);
        return new PushTokenResponse(pushToken.isEnabled());
    }

    private void trimOldDevices(Long userId) {
        List<PushToken> devices = pushTokenRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (devices.size() > MAX_DEVICES_PER_USER) {
            pushTokenRepository.deleteAll(devices.subList(MAX_DEVICES_PER_USER, devices.size()));
        }
    }

    static String validateToken(String token) {
        String trimmed = token == null ? "" : token.trim();
        if (!TOKEN_FORMAT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("올바르지 않은 푸시 토큰입니다.");
        }
        return trimmed;
    }

    static String validatePlatform(String platform) {
        String lower = platform == null ? "" : platform.trim().toLowerCase();
        if (!PLATFORMS.contains(lower)) {
            throw new IllegalArgumentException("기기 종류는 ios 또는 android여야 합니다.");
        }
        return lower;
    }

    // ===================== 푸시 보내기 =====================

    // 알림이 DB에 확정(커밋)된 뒤에 푸시를 보낸다. 트랜잭션이 없으면 바로 보낸다.
    // 보내는 일은 따로 돌려서, 푸시 서버가 느려도 알림을 만드는 쪽(스케줄러, 댓글 작성)은 기다리지 않는다.
    public void pushAfterCommit(Collection<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }
        List<Notification> copy = List.copyOf(notifications);
        Runnable send = () -> executor.execute(() -> {
            try {
                push(copy);
            } catch (Exception e) {
                log.warn("푸시를 보내지 못했습니다: {}", e.getClass().getSimpleName());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    // 알림 목록에는 남기지 않는 운영용 푸시 (관리자에게 알려주기 등). 푸시를 켠 기기에만 간다.
    public void sendToUsers(Collection<Long> userIds, String title, String body, Map<String, Object> data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(userIds);
        executor.execute(() -> {
            try {
                List<Map<String, Object>> messages = new ArrayList<>();
                List<String> recipients = new ArrayList<>();
                for (PushToken token : pushTokenRepository.findByUserIdInAndEnabledTrue(ids)) {
                    messages.add(buildMessage(token.getToken(), title, body, data, null));
                    recipients.add(token.getToken());
                }
                deliver(messages, recipients);
            } catch (Exception e) {
                log.warn("푸시를 보내지 못했습니다: {}", e.getClass().getSimpleName());
            }
        });
    }

    // 알림들을 받는 사람의 (푸시를 켠) 모든 기기로 보낸다. 여러 사람 것을 모아서 100개씩 나눠 보낸다.
    void push(Collection<Notification> notifications) {
        Set<Long> userIds = notifications.stream().map(Notification::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<PushToken>> tokensByUser = pushTokenRepository.findByUserIdInAndEnabledTrue(userIds).stream()
                .collect(Collectors.groupingBy(PushToken::getUserId));
        if (tokensByUser.isEmpty()) {
            return;
        }
        Map<Long, Long> unread = unreadCounts(tokensByUser.keySet());

        List<Map<String, Object>> messages = new ArrayList<>();
        List<String> recipients = new ArrayList<>();
        for (Notification notification : notifications) {
            for (PushToken token : tokensByUser.getOrDefault(notification.getUserId(), List.of())) {
                messages.add(buildMessage(token.getToken(), notification.getTitle(), notification.getMessage(),
                        data(notification), unread.get(notification.getUserId())));
                recipients.add(token.getToken());
            }
        }
        deliver(messages, recipients);
    }

    // 내 기기로 테스트 푸시를 보낸다 (앱의 "테스트 알림 보내기"). 계정당 1분에 한 번만.
    public int sendTest(Long userId) {
        Instant now = clock.instant();
        Instant last = lastTestAt.get(userId);
        if (last != null && now.isBefore(last.plus(TEST_COOLDOWN))) {
            throw new DuplicateRequestException("테스트 알림은 1분에 한 번만 보낼 수 있어요. 잠시 후 다시 시도해주세요.");
        }
        List<PushToken> tokens = pushTokenRepository.findByUserIdInAndEnabledTrue(List.of(userId));
        if (tokens.isEmpty()) {
            throw new IllegalStateException("푸시를 받을 수 있는 기기가 없어요. 앱에서 알림 권한과 푸시 설정을 확인해주세요.");
        }
        lastTestAt.put(userId, now);

        List<Map<String, Object>> messages = new ArrayList<>();
        List<String> recipients = new ArrayList<>();
        for (PushToken token : tokens) {
            messages.add(buildMessage(token.getToken(), "PICKSTAGE 테스트 알림", "푸시 알림이 정상으로 도착했어요.",
                    Map.of("type", "test"), null));
            recipients.add(token.getToken());
        }
        return deliver(messages, recipients);
    }

    // 100개씩 나눠 보내고, 더는 유효하지 않은 토큰(앱 삭제 등)은 정리한다. 푸시 서버가 받아준 개수를 돌려준다.
    private int deliver(List<Map<String, Object>> messages, List<String> recipients) {
        int accepted = 0;
        Set<String> invalidTokens = new LinkedHashSet<>();
        for (int from = 0; from < messages.size(); from += CHUNK_SIZE) {
            int to = Math.min(from + CHUNK_SIZE, messages.size());
            List<ExpoPushClient.Ticket> tickets = expoPushClient.send(messages.subList(from, to));
            for (int i = 0; i < tickets.size(); i++) {
                ExpoPushClient.Ticket ticket = tickets.get(i);
                if (ticket.ok()) {
                    accepted++;
                } else if (ticket.deviceNotRegistered()) {
                    invalidTokens.add(recipients.get(from + i));
                } else {
                    log.warn("푸시가 거절되었습니다: {}", ticket.errorCode());
                }
            }
        }
        if (!invalidTokens.isEmpty()) {
            pushTokenRepository.deleteAllByTokenIn(invalidTokens);
            log.info("더는 유효하지 않은 푸시 토큰 {}개를 정리했습니다.", invalidTokens.size());
        }
        return accepted;
    }

    private Map<Long, Long> unreadCounts(Collection<Long> userIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : notificationRepository.countUnreadByUserIds(userIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    // 앱이 알림을 눌렀을 때 어디로 갈지 알 수 있는 정보 (공연이면 festivalId, 댓글이면 postId)
    static Map<String, Object> data(Notification notification) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", notification.getType() == null ? "custom" : notification.getType().name());
        if (notification.getId() != null) {
            data.put("notificationId", notification.getId());
        }
        if (notification.getFestivalId() != null) {
            data.put("festivalId", notification.getFestivalId());
        }
        if (notification.getPostId() != null) {
            data.put("postId", notification.getPostId());
        }
        return data;
    }

    static Map<String, Object> buildMessage(String to, String title, String body, Map<String, Object> data,
            Long badge) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", to);
        message.put("title", title);
        message.put("body", body);
        message.put("sound", "default");
        message.put("priority", "high");
        message.put("data", data);
        if (badge != null) {
            message.put("badge", badge);
        }
        return message;
    }
}
