package com.pickstage.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalUpdateSuggestion;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.UserRepository;

// 하루 한 번 예정 공연을 KOPIS에서 확인해, 새 출연진과 새 예매처가 있으면 제안으로 쌓고 관리자에게 알린다.
// 공연에 바로 반영하지 않는다 (관리자 승인 필요).
@Component
public class FestivalUpdateScanner {

    private static final Logger log = LoggerFactory.getLogger(FestivalUpdateScanner.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_MAIL_LINES = 30;

    // 마지막 확인 결과 (서버가 재시작되면 지워진다)
    public record Status(boolean configured, boolean running, LocalDateTime finishedAt,
            int checked, int matched, int created, int failed) {
    }

    private final FestivalRepository festivalRepository;
    private final UserRepository userRepository;
    private final KopisPerformanceService kopis;
    private final FestivalUpdateSuggestionService suggestionService;
    private final EmailService emailService;
    private final ReportNotifier reportNotifier;
    private final PushService pushService;
    private final String frontendUrl;
    private final Executor executor;
    private final long pauseMillis;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile LocalDateTime finishedAt;
    private volatile int checked;
    private volatile int matched;
    private volatile int created;
    private volatile int failed;

    @Autowired
    public FestivalUpdateScanner(FestivalRepository festivalRepository, UserRepository userRepository,
            KopisPerformanceService kopis, FestivalUpdateSuggestionService suggestionService, EmailService emailService,
            ReportNotifier reportNotifier, PushService pushService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this(festivalRepository, userRepository, kopis, suggestionService, emailService, reportNotifier, pushService,
                frontendUrl, ForkJoinPool.commonPool(), 200);
    }

    // 테스트에서 바로 실행하고 쉬지 않도록 바꿀 때 쓴다
    FestivalUpdateScanner(FestivalRepository festivalRepository, UserRepository userRepository,
            KopisPerformanceService kopis, FestivalUpdateSuggestionService suggestionService, EmailService emailService,
            ReportNotifier reportNotifier, PushService pushService, String frontendUrl, Executor executor,
            long pauseMillis) {
        this.festivalRepository = festivalRepository;
        this.userRepository = userRepository;
        this.kopis = kopis;
        this.suggestionService = suggestionService;
        this.emailService = emailService;
        this.reportNotifier = reportNotifier;
        this.pushService = pushService;
        this.frontendUrl = frontendUrl;
        this.executor = executor;
        this.pauseMillis = pauseMillis;
    }

    // 매일 오전 10시(한국 시간). 오래 걸려서 다른 예약 작업이 밀리지 않도록 따로 돌린다.
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void scheduledScan() {
        start();
    }

    // 관리자 화면의 "지금 확인"
    public Status startByAdmin(Long adminId) {
        requireAdmin(adminId);
        start();
        return status();
    }

    public Status getStatus(Long adminId) {
        requireAdmin(adminId);
        return status();
    }

    private Status status() {
        return new Status(kopis.isConfigured(), running.get(), finishedAt, checked, matched, created, failed);
    }

    // 이미 확인 중이면 아무것도 하지 않는다
    private void start() {
        if (!kopis.isConfigured() || !running.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                scan();
            } catch (Exception e) {
                log.warn("공연 정보 자동 확인에 실패했어요: {}", e.getClass().getSimpleName());
            } finally {
                finishedAt = LocalDateTime.now(KOREA);
                running.set(false);
            }
        });
    }

    private void scan() {
        checked = 0;
        matched = 0;
        created = 0;
        failed = 0;
        List<String> lines = new ArrayList<>();
        for (Festival festival : festivalRepository.findByEndDateGreaterThanEqual(LocalDate.now(KOREA))) {
            try {
                checked++;
                var found = kopis.find(festival.getTitle(), festival.getStartDate(), festival.getEndDate());
                if (found.isPresent()) {
                    matched++;
                    List<FestivalUpdateSuggestion> added = suggestionService.addFromSource(
                            festival, found.get().cast(), toTickets(found.get()));
                    created += added.size();
                    added.forEach(s -> lines.add(describe(festival, s)));
                }
                pause();
            } catch (Exception e) {
                failed++;
                log.warn("공연 {} 확인에 실패했어요: {}", festival.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("공연 정보 자동 확인: {}건 확인, {}건 일치, 새 제안 {}건, 실패 {}건", checked, matched, created, failed);
        if (!lines.isEmpty()) {
            notifyAdmins(lines);
        }
    }

    // 예매처 링크마다 KOPIS의 가격표를 붙여 티켓 후보를 만든다 (가격표가 없으면 '일반' 하나)
    static List<TicketDraft> toTickets(KopisPerformanceService.Performance performance) {
        List<TicketDraft> tickets = new ArrayList<>();
        for (KopisPerformanceService.Vendor vendor : performance.vendors()) {
            String known = TicketPageService.vendorNameOf(vendor.url());
            String name = known != null ? known : vendor.name();
            // 예매처가 아닌 링크(공식 홈페이지 등)는 건너뛴다
            if (!FestivalDiffService.isTicketVendor(name)) {
                continue;
            }
            List<KopisPerformanceService.Price> prices = performance.prices().isEmpty()
                    ? List.of(new KopisPerformanceService.Price("일반", null))
                    : performance.prices();
            for (KopisPerformanceService.Price price : prices) {
                tickets.add(TicketDraft.builder()
                        .vendorName(name)
                        .label(price.label())
                        .price(price.amount())
                        .bookingUrl(vendor.url())
                        .build());
            }
        }
        return tickets;
    }

    private static String describe(Festival festival, FestivalUpdateSuggestion s) {
        String what = s.getType() == FestivalUpdateSuggestion.Type.LINEUP
                ? "라인업: " + s.getArtistName()
                : "티켓: " + s.getVendorName() + " " + s.getLabel();
        return festival.getTitle() + " - " + what;
    }

    private void notifyAdmins(List<String> lines) {
        String subject = "[PickStage] 새 공연 정보 제안 " + lines.size() + "건";
        StringBuilder body = new StringBuilder("KOPIS에서 새로 확인한 정보예요. 승인해야 공연에 반영돼요.\n\n");
        lines.stream().limit(MAX_MAIL_LINES).forEach(line -> body.append("- ").append(line).append('\n'));
        if (lines.size() > MAX_MAIL_LINES) {
            body.append("외 ").append(lines.size() - MAX_MAIL_LINES).append("건\n");
        }
        body.append("\n관리자 페이지 > 정보 갱신에서 확인해주세요.\n").append(frontendUrl).append("/admin\n출처: KOPIS(공연예술통합전산망)");

        for (String to : reportNotifier.recipients()) {
            try {
                emailService.sendAdminNotice(to, subject, body.toString());
            } catch (Exception e) {
                log.warn("제안 알림 메일 발송 실패: {}", e.getClass().getSimpleName());
            }
        }
        try {
            pushService.sendToUsers(
                    userRepository.findByRoleAndDeletedAtIsNull(User.Role.admin).stream().map(User::getId).toList(),
                    "새 공연 정보 제안 " + lines.size() + "건", "관리자 페이지에서 확인해주세요.", Map.of("type", "admin"));
        } catch (Exception e) {
            log.warn("제안 알림 푸시 발송 실패: {}", e.getClass().getSimpleName());
        }
    }

    private void pause() {
        if (pauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
