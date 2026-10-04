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

    @Value("${app.frontend-url}")
    private String frontendUrl;

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

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject(
                "[PickStage] 비밀번호 재설정 안내"
        );

        message.setText(
                "아래 링크를 통해 비밀번호를 재설정해주세요.\n\n"
                        + resetLink
                        + "\n\n"
                        + "해당 링크는 30분 동안 유효합니다."
        );

        mailSender.send(message);
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

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject(
                "[PickStage] 이메일 인증코드 안내"
        );

        message.setText(
                "PickStage 이메일 인증코드는 "
                        + verifyCode
                        + "입니다.\n\n"
                        + "인증코드는 5분 동안 유효합니다."
        );

        mailSender.send(message);
    }
}