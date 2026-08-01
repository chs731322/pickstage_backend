package com.pickstage.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public EmailService(
            JavaMailSender mailSender,
            EmailVerificationRepository emailVerificationRepository,
            UserRepository userRepository) {

        this.mailSender = mailSender;
        this.emailVerificationRepository = emailVerificationRepository;
        this.userRepository = userRepository;
    }

    // 이메일 인증코드 발송
    @Transactional
    public void sendVerificationCode(String email) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String verifyCode = generateVerificationCode();

        LocalDateTime expiredAt = LocalDateTime.now()
                .plusMinutes(VERIFICATION_EXPIRATION_MINUTES);

        sendEmail(email, verifyCode);

        EmailVerification verification =
                emailVerificationRepository.findByEmail(email)
                        .orElse(null);

        if (verification == null) {
            verification = EmailVerification.builder()
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
    public void verifyEmail(String email, String verifyCode) {

        EmailVerification verification =
                emailVerificationRepository.findByEmail(email)
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

        if (!verification.getVerifyCode().equals(verifyCode)) {
            throw new IllegalArgumentException(
                    "인증코드가 틀렸습니다."
            );
        }

        verification.completeVerification();
    }

    // 이메일 인증 완료 여부 확인
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String email) {

        return emailVerificationRepository.findByEmail(email)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    // 6자리 인증코드 생성
    private String generateVerificationCode() {

        int number = secureRandom.nextInt(
                VERIFICATION_CODE_BOUND
        );

        return String.format("%06d", number);
    }

    // 인증코드 이메일 발송
    private void sendEmail(String email, String verifyCode) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject("[PickStage] 이메일 인증코드 안내");
        message.setText(
                "PickStage 이메일 인증코드는 "
                        + verifyCode
                        + "입니다.\n\n"
                        + "인증코드는 5분 동안 유효합니다."
        );

        mailSender.send(message);
    }
}