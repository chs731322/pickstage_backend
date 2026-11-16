package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.pickstage.backend.dto.AdminReportResolveRequest;
import com.pickstage.backend.dto.AdminReportResponse;
import com.pickstage.backend.entity.Comment;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.Report;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.PushTokenRepository;
import com.pickstage.backend.repository.RefreshTokenRepository;
import com.pickstage.backend.repository.ReportRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

class AdminReportServiceTest {

    private static final Long ADMIN = 1L;

    private ReportRepository reportRepository;
    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private VenueReviewRepository venueReviewRepository;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PushTokenRepository pushTokenRepository;
    private AdminReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        venueReviewRepository = mock(VenueReviewRepository.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        pushTokenRepository = mock(PushTokenRepository.class);
        service = new AdminReportService(reportRepository, postRepository, commentRepository, venueReviewRepository,
                userRepository, refreshTokenRepository, pushTokenRepository);
        when(userRepository.findById(ADMIN)).thenReturn(Optional.of(User.builder().id(ADMIN).role(User.Role.admin).build()));
    }

    private AdminReportResolveRequest resolve(Report.TargetType type, Long id, AdminReportResolveRequest.Action action) {
        return new AdminReportResolveRequest(type, id, action);
    }

    private User member(Long id, String nickname) {
        return User.builder().id(id).nickname(nickname).role(User.Role.user).build();
    }

    // ---- 권한 ----

    @Test
    void 관리자가_아니면_모든_기능을_쓸_수_없다() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(member(5L, "일반")));

        assertThrows(AccessDeniedException.class, () -> service.getReports(5L, false));
        assertThrows(AccessDeniedException.class,
                () -> service.resolve(5L, resolve(Report.TargetType.POST, 10L, AdminReportResolveRequest.Action.DELETE)));
        assertThrows(AccessDeniedException.class, () -> service.banUser(5L, 7L));
        assertThrows(AccessDeniedException.class, () -> service.unbanUser(5L, 7L));
        verify(reportRepository, never()).markHandled(any(), any(), any(), any(), any(), any());
    }

    // ---- 목록 ----

    @SuppressWarnings("unchecked")
    @Test
    void 신고_목록은_같은_대상을_묶고_사유_수와_본문_앞부분을_보여준다() {
        LocalDateTime last = LocalDateTime.now();
        when(reportRepository.findGroups(any(Collection.class), any(Pageable.class))).thenReturn(List.<Object[]>of(
                new Object[] { Report.TargetType.POST, 10L, 3L, last }));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(
                eq(Report.TargetType.POST), eq(10L), any(Collection.class))).thenReturn(List.of(
                Report.builder().reason(Report.Reason.SPAM).detail("광고").build(),
                Report.builder().reason(Report.Reason.SPAM).build(),
                Report.builder().reason(Report.Reason.ABUSE).detail("욕설").build()));
        when(postRepository.findById(10L)).thenReturn(Optional.of(Post.builder().id(10L).userId(2L)
                .title("제목").content("본문 ![설명](https://x/y.png)\n두 번째 줄").hiddenAt(last).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member(2L, "작성자")));

        List<AdminReportResponse> list = service.getReports(ADMIN, false);

        assertEquals(1, list.size());
        AdminReportResponse r = list.get(0);
        assertEquals("POST", r.getTargetType());
        assertEquals(3, r.getReportCount());
        assertEquals(2L, r.getReasons().get("SPAM"));
        assertEquals(1L, r.getReasons().get("ABUSE"));
        assertEquals(List.of("광고", "욕설"), r.getDetails());
        assertTrue(r.isHidden());
        assertFalse(r.isRemoved());
        assertEquals("제목", r.getTitle());
        assertEquals("본문 [사진] 두 번째 줄", r.getSnippet());
        assertEquals("작성자", r.getAuthorNickname());
        assertFalse(r.isAuthorBanned());
    }

    @SuppressWarnings("unchecked")
    @Test
    void 댓글과_후기와_사용자_신고도_보여주고_삭제된_대상은_표시한다() {
        when(reportRepository.findGroups(any(Collection.class), any(Pageable.class))).thenReturn(List.<Object[]>of(
                new Object[] { Report.TargetType.COMMENT, 5L, 1L, LocalDateTime.now() },
                new Object[] { Report.TargetType.VENUE_REVIEW, 6L, 1L, LocalDateTime.now() },
                new Object[] { Report.TargetType.USER, 3L, 2L, LocalDateTime.now() },
                new Object[] { Report.TargetType.POST, 99L, 1L, LocalDateTime.now() }));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatusInOrderByCreatedAtAsc(any(), any(), any(Collection.class)))
                .thenReturn(List.of(Report.builder().reason(Report.Reason.OTHER).detail("기타").build()));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(Comment.builder().id(5L).userId(2L).postId(77L).content("댓글").build()));
        when(venueReviewRepository.findById(6L)).thenReturn(Optional.of(VenueReview.builder().id(6L).userId(2L).venueId(88L).content("후기").build()));
        User banned = member(3L, "정지됨");
        banned.ban();
        when(userRepository.findById(3L)).thenReturn(Optional.of(banned));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member(2L, "작성자")));
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        List<AdminReportResponse> list = service.getReports(ADMIN, false);

        assertEquals(77L, list.get(0).getPostId());
        assertEquals(88L, list.get(1).getVenueId());
        assertEquals("정지됨", list.get(2).getTitle());
        assertTrue(list.get(2).isAuthorBanned());
        assertTrue(list.get(3).isRemoved());
    }

    // ---- 처리 ----

    @Test
    void 삭제로_처리하면_글이_삭제되고_신고가_처리_완료가_된다() {
        Post post = Post.builder().id(10L).userId(2L).hiddenAt(LocalDateTime.now()).build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.markHandled(eq(Report.TargetType.POST), eq(10L), eq(Report.Status.PENDING),
                eq(Report.Status.DELETED), any(), eq(ADMIN))).thenReturn(3);

        service.resolve(ADMIN, resolve(Report.TargetType.POST, 10L, AdminReportResolveRequest.Action.DELETE));

        assertNotNull(post.getDeletedAt());
        verify(postRepository).save(post);
    }

    @Test
    void 문제없음으로_처리하면_숨김이_풀리고_삭제되지_않는다() {
        Comment comment = Comment.builder().id(5L).userId(2L).hiddenAt(LocalDateTime.now()).build();
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));
        when(reportRepository.markHandled(eq(Report.TargetType.COMMENT), eq(5L), eq(Report.Status.PENDING),
                eq(Report.Status.DISMISSED), any(), eq(ADMIN))).thenReturn(2);

        service.resolve(ADMIN, resolve(Report.TargetType.COMMENT, 5L, AdminReportResolveRequest.Action.DISMISS));

        assertNull(comment.getHiddenAt());
        assertNull(comment.getDeletedAt());
        verify(commentRepository).save(comment);
    }

    @Test
    void 후기도_같은_방식으로_처리한다() {
        VenueReview review = VenueReview.builder().id(6L).userId(2L).hiddenAt(LocalDateTime.now()).build();
        when(venueReviewRepository.findById(6L)).thenReturn(Optional.of(review));
        when(reportRepository.markHandled(any(), any(), any(), any(), any(), any())).thenReturn(1);

        service.resolve(ADMIN, resolve(Report.TargetType.VENUE_REVIEW, 6L, AdminReportResolveRequest.Action.DELETE));

        assertNotNull(review.getDeletedAt());
    }

    @Test
    void 처리할_신고가_없으면_글을_건드리지_않는다() {
        when(reportRepository.markHandled(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.resolve(ADMIN, resolve(Report.TargetType.POST, 10L, AdminReportResolveRequest.Action.DELETE)));
        verify(postRepository, never()).save(any());
    }

    @Test
    void 사용자는_삭제로_처리할_수_없고_문제없음으로만_처리한다() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(ADMIN, resolve(Report.TargetType.USER, 3L, AdminReportResolveRequest.Action.DELETE)));

        when(reportRepository.markHandled(eq(Report.TargetType.USER), eq(3L), any(), eq(Report.Status.DISMISSED), any(), any()))
                .thenReturn(1);
        service.resolve(ADMIN, resolve(Report.TargetType.USER, 3L, AdminReportResolveRequest.Action.DISMISS));
        verify(userRepository, never()).save(any());
    }

    // ---- 이용 정지 ----

    @Test
    void 정지하면_로그인이_막히고_갱신_토큰이_지워지고_사용자_신고가_처리된다() {
        User target = member(3L, "문제 사용자");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        service.banUser(ADMIN, 3L);

        assertNotNull(target.getBannedAt());
        verify(userRepository).save(target);
        verify(refreshTokenRepository).deleteByUserId(3L);
        verify(pushTokenRepository).deleteByUserId(3L);
        verify(reportRepository).markHandled(eq(Report.TargetType.USER), eq(3L), eq(Report.Status.PENDING),
                eq(Report.Status.DELETED), any(), eq(ADMIN));
    }

    @Test
    void 관리자_본인_탈퇴한_사용자_이미_정지된_사용자는_정지할_수_없다() {
        User otherAdmin = User.builder().id(4L).role(User.Role.admin).build();
        User withdrawn = User.builder().id(5L).role(User.Role.user).deletedAt(LocalDateTime.now()).build();
        User alreadyBanned = member(6L, "정지");
        alreadyBanned.ban();
        when(userRepository.findById(4L)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.findById(5L)).thenReturn(Optional.of(withdrawn));
        when(userRepository.findById(6L)).thenReturn(Optional.of(alreadyBanned));

        assertThrows(IllegalArgumentException.class, () -> service.banUser(ADMIN, 4L));
        assertThrows(IllegalArgumentException.class, () -> service.banUser(ADMIN, ADMIN));
        assertThrows(IllegalStateException.class, () -> service.banUser(ADMIN, 5L));
        assertThrows(DuplicateRequestException.class, () -> service.banUser(ADMIN, 6L));
        assertThrows(IllegalStateException.class, () -> service.banUser(ADMIN, 404L));
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void 정지를_해제한다() {
        User target = member(3L, "사용자");
        target.ban();
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        service.unbanUser(ADMIN, 3L);

        assertNull(target.getBannedAt());
        verify(userRepository).save(target);
    }

    @Test
    void 정지되지_않은_사용자는_해제할_수_없다() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(member(3L, "사용자")));

        assertThrows(IllegalStateException.class, () -> service.unbanUser(ADMIN, 3L));
    }

    @Test
    void 본문_앞부분은_120자로_자르고_사진은_줄인다() {
        assertEquals("", AdminReportService.snippet(null));
        assertEquals("[사진]", AdminReportService.snippet("![a](https://x/y.png)"));
        String longText = "가".repeat(200);
        assertEquals(121, AdminReportService.snippet(longText).length());
        assertTrue(AdminReportService.snippet(longText).endsWith("…"));
    }
}
