package com.pickstage.backend.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pickstage.backend.entity.Report;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.UserRepository;

// 새 신고가 들어오면 운영자에게 이메일로 알린다 (24시간 안에 처리하기 위해).
// 받는 사람은 환경 변수 APP_REPORT_NOTIFY_EMAIL(쉼표로 여러 개)로 정하고, 비어 있으면 관리자 계정들의 이메일로 보낸다.
// 메일 발송이 느리거나 실패해도 신고 접수는 영향을 받지 않도록 따로 돌리고, 실패는 로그만 남긴다.
@Component
public class ReportNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReportNotifier.class);

    private static final Map<Report.TargetType, String> TARGET_LABELS = Map.of(
            Report.TargetType.POST, "게시글",
            Report.TargetType.COMMENT, "댓글",
            Report.TargetType.VENUE_REVIEW, "공연장 후기",
            Report.TargetType.USER, "사용자");

    private static final Map<Report.Reason, String> REASON_LABELS = Map.of(
            Report.Reason.SPAM, "스팸·광고",
            Report.Reason.ABUSE, "욕설·혐오 표현",
            Report.Reason.SEXUAL, "음란·선정적인 내용",
            Report.Reason.ILLEGAL, "불법·사기 의심",
            Report.Reason.PRIVACY, "개인정보 노출",
            Report.Reason.OTHER, "기타");

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final String notifyEmails;
    private final String frontendUrl;
    private final Executor executor;

    @Autowired
    public ReportNotifier(EmailService emailService, UserRepository userRepository,
            @Value("${app.report-notify-email:}") String notifyEmails,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this(emailService, userRepository, notifyEmails, frontendUrl, ForkJoinPool.commonPool());
    }

    // 테스트에서 바로(같은 스레드에서) 실행하도록 실행기를 바꿀 때 쓴다
    ReportNotifier(EmailService emailService, UserRepository userRepository, String notifyEmails, String frontendUrl,
            Executor executor) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.notifyEmails = notifyEmails;
        this.frontendUrl = frontendUrl;
        this.executor = executor;
    }

    // pendingCount: 이 대상에 쌓인 처리 전 신고 수 (방금 접수한 것 포함)
    public void notifyNewReport(Report report, long pendingCount) {
        executor.execute(() -> {
            try {
                send(report, pendingCount);
            } catch (Exception e) {
                log.warn("신고 알림 메일을 보내지 못했습니다: {}", e.getClass().getSimpleName());
            }
        });
    }

    private void send(Report report, long pendingCount) {
        Set<String> recipients = recipients();
        if (recipients.isEmpty()) {
            log.debug("신고 알림을 받을 운영자 이메일이 없어 건너뜁니다.");
            return;
        }
        String target = TARGET_LABELS.get(report.getTargetType()) + " #" + report.getTargetId();
        String reason = REASON_LABELS.get(report.getReason());

        String subject = "[PickStage] 새 신고 접수 - " + target + " (" + reason + ")";
        StringBuilder body = new StringBuilder()
                .append("새 신고가 접수되었어요.\n\n")
                .append("대상: ").append(target).append('\n')
                .append("사유: ").append(reason).append('\n');
        if (report.getDetail() != null && !report.getDetail().isBlank()) {
            body.append("내용: ").append(report.getDetail()).append('\n');
        }
        body.append("이 대상의 처리 전 신고: ").append(pendingCount).append("건\n\n")
                .append("관리자 페이지 > 신고 처리에서 확인해주세요. (24시간 안에 처리하는 것을 목표로 해요)\n")
                .append(frontendUrl).append("/admin");

        for (String to : recipients) {
            try {
                emailService.sendAdminNotice(to, subject, body.toString());
            } catch (Exception e) {
                // 한 명에게 실패해도 나머지에게는 보낸다
                log.warn("신고 알림 메일 발송 실패: {}", e.getClass().getSimpleName());
            }
        }
    }

    // 환경 변수에 적은 주소가 있으면 그 주소들, 없으면 관리자 계정들 (테스트용 더미 계정은 뺀다)
    Set<String> recipients() {
        Set<String> result = new LinkedHashSet<>();
        if (notifyEmails != null && !notifyEmails.isBlank()) {
            Arrays.stream(notifyEmails.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(result::add);
            return result;
        }
        List<User> admins = userRepository.findByRoleAndDeletedAtIsNull(User.Role.admin);
        for (User admin : admins) {
            String email = admin.getEmail();
            if (email != null && !email.endsWith("@pickstage.local")) {
                result.add(email);
            }
        }
        return result;
    }
}
