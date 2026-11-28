package com.pickstage.backend.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.WebPushSubscription;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.WebPushSubscriptionRepository;

import tools.jackson.databind.ObjectMapper;

// 관리자 브라우저로 보내는 웹 푸시. 환경 변수 WEB_PUSH_PUBLIC_KEY / WEB_PUSH_PRIVATE_KEY 가 있을 때만 동작한다.
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    // 브라우저 회사의 푸시 서버만 허용한다 (서버가 임의의 주소로 요청하지 않도록)
    private static final List<String> PUSH_HOSTS = List.of("fcm.googleapis.com", "push.apple.com",
            "push.services.mozilla.com", "notify.windows.com");

    private final WebPushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WebPushService(WebPushSubscriptionRepository repository, UserRepository userRepository, ObjectMapper objectMapper,
            @Value("${WEB_PUSH_PUBLIC_KEY:}") String publicKey, @Value("${WEB_PUSH_PRIVATE_KEY:}") String privateKey,
            @Value("${app.frontend-url:https://p1cksta9e.com}") String subject) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
    }

    public boolean isConfigured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    public String publicKey(Long adminId) {
        requireAdmin(adminId);
        return isConfigured() ? publicKey : null;
    }

    public void subscribe(Long adminId, String endpoint, String p256dh, String auth) {
        requireAdmin(adminId);
        if (!isAllowedEndpoint(endpoint) || p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank()) {
            throw new IllegalArgumentException("알림 구독 정보가 올바르지 않습니다.");
        }
        repository.deleteByEndpoint(endpoint); // 같은 브라우저가 다시 구독하면 새로 저장
        repository.save(WebPushSubscription.builder()
                .userId(adminId)
                .endpoint(endpoint)
                .p256dh(p256dh)
                .auth(auth)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void unsubscribe(Long adminId, String endpoint) {
        requireAdmin(adminId);
        repository.deleteByEndpoint(endpoint);
    }

    // 시험 알림: 몇 곳에 전달됐는지 돌려준다
    public int sendTest(Long adminId) {
        requireAdmin(adminId);
        if (!isConfigured()) {
            throw new IllegalStateException("서버에 웹 푸시 키가 설정돼 있지 않아요.");
        }
        return send(List.of(adminId), "PICKSTAGE 시험 알림", "웹 알림이 정상으로 도착했어요.", "/admin");
    }

    // 구독한 브라우저마다 보낸다. 더는 없는 구독(404/410)은 지운다. 실패는 로그만 남긴다.
    public int send(Collection<Long> userIds, String title, String body, String path) {
        if (!isConfigured() || userIds == null || userIds.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (WebPushSubscription subscription : repository.findByUserIdIn(userIds)) {
            try {
                byte[] payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", path))
                        .getBytes(StandardCharsets.UTF_8);
                int status = deliver(subscription, payload);
                if (status >= 200 && status < 300) {
                    sent++;
                } else if (status == 404 || status == 410) {
                    repository.deleteByEndpoint(subscription.getEndpoint());
                } else {
                    log.warn("웹 푸시가 거절됐어요: HTTP {}", status);
                }
            } catch (Exception e) {
                log.warn("웹 푸시를 보내지 못했어요: {}", e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private int deliver(WebPushSubscription subscription, byte[] payload) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair(); // 보낼 때마다 새 키
        ECPublicKey ephemeral = (ECPublicKey) pair.getPublic();
        byte[] senderPublic = new byte[65];
        senderPublic[0] = 4;
        copyUnsigned(ephemeral.getW().getAffineX().toByteArray(), senderPublic, 1);
        copyUnsigned(ephemeral.getW().getAffineY().toByteArray(), senderPublic, 33);
        byte[] senderPrivate = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.decode(subscription.getP256dh()),
                WebPushCrypto.decode(subscription.getAuth()), senderPrivate, senderPublic, salt);
        HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                .timeout(Duration.ofSeconds(10))
                .header("TTL", "86400")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", WebPushCrypto.vapidHeader(subscription.getEndpoint(), subject, publicKey, privateKey,
                        Instant.now().plus(Duration.ofHours(12)).getEpochSecond()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // 32바이트 자리에 맞춰 오른쪽 정렬 (BigInteger 의 앞쪽 0 처리)
    private static void copyUnsigned(byte[] value, byte[] target, int offset) {
        int from = Math.max(0, value.length - 32);
        int length = value.length - from;
        System.arraycopy(value, from, target, offset + 32 - length, length);
    }

    static boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && endpoint.length() <= 700
                    && PUSH_HOSTS.stream().anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
