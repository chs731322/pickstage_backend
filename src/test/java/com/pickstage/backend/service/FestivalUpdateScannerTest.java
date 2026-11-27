package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.FestivalUpdateSuggestion;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.UserRepository;

class FestivalUpdateScannerTest {

    private FestivalRepository festivals;
    private UserRepository users;
    private KopisPerformanceService kopis;
    private FestivalUpdateSuggestionService suggestions;
    private EmailService email;
    private ReportNotifier notifier;
    private PushService push;
    private FestivalUpdateScanner scanner;

    @BeforeEach
    void setUp() {
        festivals = mock(FestivalRepository.class);
        users = mock(UserRepository.class);
        kopis = mock(KopisPerformanceService.class);
        suggestions = mock(FestivalUpdateSuggestionService.class);
        email = mock(EmailService.class);
        notifier = mock(ReportNotifier.class);
        push = mock(PushService.class);
        scanner = new FestivalUpdateScanner(festivals, users, kopis, suggestions, email, notifier, push,
                "https://site", Runnable::run, 0);

        when(kopis.isConfigured()).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).role(User.Role.admin).build()));
        when(users.findByRoleAndDeletedAtIsNull(User.Role.admin)).thenReturn(List.of(User.builder().id(1L).build()));
        when(notifier.recipients()).thenReturn(Set.of("admin@example.com"));
        LocalDate day = LocalDate.now().plusDays(10);
        when(festivals.findByEndDateGreaterThanEqual(any())).thenReturn(List.of(
                Festival.builder().id(10L).title("찾는 공연").startDate(day).endDate(day).build(),
                Festival.builder().id(11L).title("KOPIS에 없는 공연").startDate(day).endDate(day).build()));
        when(kopis.find(eq("찾는 공연"), any(), any())).thenReturn(Optional.of(
                new KopisPerformanceService.Performance("PF", "찾는 공연", List.of("새밴드"), List.of(), List.of())));
        when(kopis.find(eq("KOPIS에 없는 공연"), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void 새로_찾은_것이_있으면_메일과_푸시로_알린다() {
        when(suggestions.addFromSource(any(), any(), any())).thenReturn(List.of(FestivalUpdateSuggestion.builder()
                .type(FestivalUpdateSuggestion.Type.LINEUP).artistName("새밴드").build()));

        var status = scanner.startByAdmin(1L);

        assertEquals(2, status.checked());
        assertEquals(1, status.matched());
        assertEquals(1, status.created());
        assertEquals(false, status.running());
        verify(email).sendAdminNotice(eq("admin@example.com"), contains("1건"), contains("새밴드"));
        verify(push).sendToUsers(eq(List.of(1L)), contains("1건"), anyString(), any());
    }

    @Test
    void 새로운_것이_없으면_알리지_않는다() {
        when(suggestions.addFromSource(any(), any(), any())).thenReturn(List.of());

        scanner.scheduledScan();

        verify(email, never()).sendAdminNotice(any(), any(), any());
    }

    @Test
    void 키가_없으면_확인하지_않고_관리자가_아니면_막는다() {
        when(kopis.isConfigured()).thenReturn(false);
        scanner.scheduledScan();
        verify(festivals, never()).findByEndDateGreaterThanEqual(any());

        when(users.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).role(User.Role.user).build()));
        assertThrows(AccessDeniedException.class, () -> scanner.startByAdmin(2L));
    }
}
