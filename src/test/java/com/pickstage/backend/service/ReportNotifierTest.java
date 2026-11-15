package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pickstage.backend.entity.Report;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.UserRepository;

class ReportNotifierTest {

    private EmailService emailService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        userRepository = mock(UserRepository.class);
    }

    // 같은 스레드에서 바로 실행해서 결과를 확인한다
    private ReportNotifier notifier(String notifyEmails) {
        return new ReportNotifier(emailService, userRepository, notifyEmails, "https://p1cksta9e.com", Runnable::run);
    }

    private Report report() {
        return Report.builder().reporterId(1L).targetType(Report.TargetType.POST).targetId(12L)
                .reason(Report.Reason.ABUSE).detail("욕설이 심해요").status(Report.Status.PENDING).build();
    }

    @Test
    void 환경_변수에_적은_주소들로_보낸다() {
        notifier(" a@example.com, b@example.com ,").notifyNewReport(report(), 2);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminNotice(eq("a@example.com"), subject.capture(), body.capture());
        verify(emailService).sendAdminNotice(eq("b@example.com"), anyString(), anyString());
        assertTrue(subject.getValue().contains("게시글 #12"));
        assertTrue(subject.getValue().contains("욕설·혐오 표현"));
        assertTrue(body.getValue().contains("욕설이 심해요"));
        assertTrue(body.getValue().contains("2건"));
        assertTrue(body.getValue().contains("https://p1cksta9e.com/admin"));
        verify(userRepository, never()).findByRoleAndDeletedAtIsNull(any());
    }

    @Test
    void 주소를_정하지_않으면_관리자_계정에게_보내고_더미_계정은_뺀다() {
        when(userRepository.findByRoleAndDeletedAtIsNull(User.Role.admin)).thenReturn(List.of(
                User.builder().email("admin@example.com").build(),
                User.builder().email("dummy-1@pickstage.local").build()));

        notifier("").notifyNewReport(report(), 1);

        verify(emailService).sendAdminNotice(eq("admin@example.com"), anyString(), contains("신고"));
        verify(emailService, never()).sendAdminNotice(eq("dummy-1@pickstage.local"), anyString(), anyString());
    }

    @Test
    void 받을_사람이_없으면_보내지_않는다() {
        when(userRepository.findByRoleAndDeletedAtIsNull(User.Role.admin)).thenReturn(List.of());

        notifier("").notifyNewReport(report(), 1);

        verify(emailService, never()).sendAdminNotice(anyString(), anyString(), anyString());
    }

    @Test
    void 한_명에게_보내지_못해도_나머지에게는_보내고_예외는_밖으로_나가지_않는다() {
        doThrow(new RuntimeException("메일 서버 오류")).when(emailService)
                .sendAdminNotice(eq("a@example.com"), anyString(), anyString());

        notifier("a@example.com,b@example.com").notifyNewReport(report(), 1);

        verify(emailService).sendAdminNotice(eq("b@example.com"), anyString(), anyString());
    }

    @Test
    void 받는_사람_주소는_중복을_없앤다() {
        Set<String> recipients = notifier("a@example.com, a@example.com").recipients();
        assertEquals(Set.of("a@example.com"), recipients);
    }
}
