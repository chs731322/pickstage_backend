package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pickstage.backend.dto.ReportCreateRequest;
import com.pickstage.backend.entity.Comment;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.Report;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.global.exception.DuplicateRequestException;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.ReportRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

class ReportServiceTest {

    private ReportRepository reportRepository;
    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private VenueReviewRepository venueReviewRepository;
    private UserRepository userRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        venueReviewRepository = mock(VenueReviewRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ReportService(reportRepository, postRepository, commentRepository, venueReviewRepository,
                userRepository);
    }

    private ReportCreateRequest request(Report.TargetType type, Long targetId, Report.Reason reason, String detail) {
        return new ReportCreateRequest(type, targetId, reason, detail);
    }

    @Test
    void 게시글을_신고하면_처리_전_상태로_저장된다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(Post.builder().id(10L).userId(2L).build()));

        service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.SPAM, "  광고예요  "));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertEquals(1L, saved.getReporterId());
        assertEquals(Report.TargetType.POST, saved.getTargetType());
        assertEquals(10L, saved.getTargetId());
        assertEquals("광고예요", saved.getDetail());
        assertEquals(Report.Status.PENDING, saved.getStatus());
    }

    @Test
    void 댓글_후기_사용자도_신고할_수_있다() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(Comment.builder().id(5L).userId(2L).build()));
        when(venueReviewRepository.findById(6L)).thenReturn(Optional.of(VenueReview.builder().id(6L).userId(2L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));

        service.createReport(1L, request(Report.TargetType.COMMENT, 5L, Report.Reason.ABUSE, null));
        service.createReport(1L, request(Report.TargetType.VENUE_REVIEW, 6L, Report.Reason.SEXUAL, null));
        service.createReport(1L, request(Report.TargetType.USER, 2L, Report.Reason.ILLEGAL, null));

        verify(reportRepository, org.mockito.Mockito.times(3)).save(any(Report.class));
    }

    @Test
    void 없거나_삭제된_대상은_신고할_수_없다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(
                Post.builder().id(10L).userId(2L).deletedAt(LocalDateTime.now()).build()));

        assertThrows(IllegalStateException.class,
                () -> service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.SPAM, null)));
        assertThrows(IllegalStateException.class,
                () -> service.createReport(1L, request(Report.TargetType.COMMENT, 99L, Report.Reason.SPAM, null)));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void 탈퇴한_사용자는_신고할_수_없다() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).deletedAt(LocalDateTime.now()).build()));

        assertThrows(IllegalStateException.class,
                () -> service.createReport(1L, request(Report.TargetType.USER, 2L, Report.Reason.SPAM, null)));
    }

    @Test
    void 내가_쓴_글과_나_자신은_신고할_수_없다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(Post.builder().id(10L).userId(1L).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThrows(IllegalArgumentException.class,
                () -> service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.SPAM, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createReport(1L, request(Report.TargetType.USER, 1L, Report.Reason.SPAM, null)));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void 기타_사유는_자세한_내용이_있어야_한다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(Post.builder().id(10L).userId(2L).build()));

        assertThrows(IllegalArgumentException.class,
                () -> service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.OTHER, "   ")));
        assertThrows(IllegalArgumentException.class,
                () -> service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.OTHER, null)));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void 같은_대상을_두_번_신고할_수_없다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(Post.builder().id(10L).userId(2L).build()));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, Report.TargetType.POST, 10L))
                .thenReturn(true);

        assertThrows(DuplicateRequestException.class,
                () -> service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.SPAM, null)));
        verify(reportRepository, never()).save(any());
    }

    // ---- 신고가 쌓이면 임시로 숨김 ----

    @Test
    void 처리_전_신고가_3건_쌓이면_게시글을_숨긴다() {
        Post post = Post.builder().id(10L).userId(2L).build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.POST, 10L, Report.Status.PENDING))
                .thenReturn(3L);

        service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.ABUSE, null));

        assertNotNull(post.getHiddenAt());
        verify(postRepository).save(post);
    }

    @Test
    void 신고가_3건_미만이면_숨기지_않는다() {
        Post post = Post.builder().id(10L).userId(2L).build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.POST, 10L, Report.Status.PENDING))
                .thenReturn(2L);

        service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.ABUSE, null));

        assertNull(post.getHiddenAt());
        verify(postRepository, never()).save(any());
    }

    @Test
    void 댓글과_후기도_3건이면_숨긴다() {
        Comment comment = Comment.builder().id(5L).userId(2L).build();
        VenueReview review = VenueReview.builder().id(6L).userId(2L).build();
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));
        when(venueReviewRepository.findById(6L)).thenReturn(Optional.of(review));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.COMMENT, 5L, Report.Status.PENDING))
                .thenReturn(3L);
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.VENUE_REVIEW, 6L, Report.Status.PENDING))
                .thenReturn(4L);

        service.createReport(1L, request(Report.TargetType.COMMENT, 5L, Report.Reason.SPAM, null));
        service.createReport(1L, request(Report.TargetType.VENUE_REVIEW, 6L, Report.Reason.SPAM, null));

        assertNotNull(comment.getHiddenAt());
        assertNotNull(review.getHiddenAt());
    }

    @Test
    void 이미_숨긴_글은_숨긴_시각을_바꾸지_않는다() {
        LocalDateTime hiddenAt = LocalDateTime.now().minusDays(1);
        Post post = Post.builder().id(10L).userId(2L).hiddenAt(hiddenAt).build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.POST, 10L, Report.Status.PENDING))
                .thenReturn(5L);

        service.createReport(1L, request(Report.TargetType.POST, 10L, Report.Reason.SPAM, null));

        assertEquals(hiddenAt, post.getHiddenAt());
        verify(postRepository, never()).save(any());
    }

    @Test
    void 사용자_신고는_쌓여도_자동으로_숨기지_않는다() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(Report.TargetType.USER, 2L, Report.Status.PENDING))
                .thenReturn(10L);

        service.createReport(1L, request(Report.TargetType.USER, 2L, Report.Reason.ABUSE, null));

        verify(reportRepository, never()).countByTargetTypeAndTargetIdAndStatus(any(), any(), any());
    }
}
