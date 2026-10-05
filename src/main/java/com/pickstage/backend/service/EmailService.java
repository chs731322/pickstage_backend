package com.pickstage.backend.service;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.pickstage.backend.entity.EmailVerification;
import com.pickstage.backend.repository.EmailVerificationRepository;
import com.pickstage.backend.repository.UserRepository;

@Service
public class EmailService {

    private static final int VERIFICATION_CODE_BOUND = 1_000_000;
    private static final int VERIFICATION_EXPIRATION_MINUTES = 5;

    private final JavaMailSender mailSender;
    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Railway Trial/Hobby 플랜은 SMTP(25/465/587)가 막혀 있어, 키가 있으면 HTTPS API(Resend)로 발송하고
    // 없으면(로컬 개발 등) 기존 SMTP로 발송한다.
    @Value("${resend.api-key:}")
    private String resendApiKey;

    // Resend 발신 주소 (인증한 도메인 기준, 예: PickStage <no-reply@example.xyz>)
    @Value("${mail.from:}")
    private String mailFrom;

    private final RestClient resendClient = buildResendClient();

    public EmailService(
            JavaMailSender mailSender,
            EmailVerificationRepository emailVerificationRepository,
            UserRepository userRepository) {

        this.mailSender = mailSender;
        this.emailVerificationRepository =
                emailVerificationRepository;
        this.userRepository = userRepository;
    }

    // 이메일 인증코드 발송
    @Transactional
    public void sendVerificationCode(String email) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 가입된 이메일입니다."
            );
        }

        String verifyCode = generateVerificationCode();

        LocalDateTime expiredAt =
                LocalDateTime.now()
                        .plusMinutes(
                                VERIFICATION_EXPIRATION_MINUTES
                        );

        sendEmail(email, verifyCode);

        EmailVerification verification =
                emailVerificationRepository
                        .findByEmail(email)
                        .orElse(null);

        if (verification == null) {

            verification =
                    EmailVerification.builder()
                            .email(email)
                            .verifyCode(verifyCode)
                            .expiredAt(expiredAt)
                            .verified(false)
                            .build();

        } else {

            verification.updateVerificationCode(
                    verifyCode,
                    expiredAt
            );
        }

        emailVerificationRepository.save(verification);
    }

    // 이메일 인증코드 검증
    @Transactional
    public void verifyEmail(
            String email,
            String verifyCode) {

        EmailVerification verification =
                emailVerificationRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "인증코드가 틀렸습니다."
                                )
                        );

        if (verification.isExpired()) {
            throw new IllegalArgumentException(
                    "인증코드가 만료되었습니다."
            );
        }

        if (!verification
                .getVerifyCode()
                .equals(verifyCode)) {

            throw new IllegalArgumentException(
                    "인증코드가 틀렸습니다."
            );
        }

        verification.completeVerification();
    }

    // 이메일 인증 완료 여부 확인
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String email) {

        return emailVerificationRepository
                .findByEmail(email)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    // 회원가입 완료 후 이메일 인증정보 삭제
    @Transactional
    public void deleteVerification(String email) {
        emailVerificationRepository.deleteByEmail(email);
    }

    // 비밀번호 재설정 이메일 발송
    public void sendPasswordResetEmail(
            String email,
            String resetToken) {

        String resetLink =
                frontendUrl
                        + "/reset-password?token="
                        + resetToken;

        deliver(
                email,
                "[PickStage] 비밀번호 재설정 안내",
                "아래 링크를 통해 비밀번호를 재설정해주세요.\n\n"
                        + resetLink
                        + "\n\n"
                        + "해당 링크는 30분 동안 유효합니다."
        );
    }

    private static RestClient buildResendClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    // 메일 발송: Resend 키가 있으면 HTTPS API, 없으면 SMTP
    private void deliver(String to, String subject, String text) {

        if (resendApiKey == null || resendApiKey.isBlank()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            return;
        }

        String from = (mailFrom == null || mailFrom.isBlank()) ? senderEmail : mailFrom;

        try {
            resendClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(to),
                            "subject", subject,
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 전역 핸들러가 MailException을 "이메일 발송에 실패했습니다."로 응답한다
            throw new MailSendException("Resend 이메일 발송 실패", e);
        }
    }

    // 6자리 인증코드 생성
    private String generateVerificationCode() {

        int number =
                secureRandom.nextInt(
                        VERIFICATION_CODE_BOUND
                );

        return String.format(
                "%06d",
                number
        );
    }

    // 인증코드 이메일 발송
    private void sendEmail(
            String email,
            String verifyCode) {

        deliver(
                email,
                "[PickStage] 이메일 인증코드 안내",
                "PickStage 이메일 인증코드는 "
                        + verifyCode
                        + "입니다.\n\n"
                        + "인증코드는 5분 동안 유효합니다."
        );
    }
}
