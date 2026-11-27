package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pickstage.backend.dto.PostCreateRequest;
import com.pickstage.backend.dto.PostDetailResponse;
import com.pickstage.backend.dto.PostListResponse;
import com.pickstage.backend.entity.Comment;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.repository.CommentRepository;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.PostImageRepository;
import com.pickstage.backend.repository.PostLikeRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.UserRepository;

// 차단한 사용자의 글·댓글 숨기기, 신고로 숨긴 글 막기, 금칙어 거절
class PostServiceBlockingTest {

    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private BlockService blockService;
    private PostService service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        blockService = mock(BlockService.class);
        service = new PostService(postRepository, mock(FestivalRepository.class), mock(UserRepository.class),
                commentRepository, mock(PostImageRepository.class), mock(PostLikeRepository.class),
                mock(ViewGuard.class), blockService, new ProfanityFilter());
    }

    private Post post(Long id, Long authorId) {
        return Post.builder().id(id).userId(authorId).category(Post.Category.free).title("제목").content("내용")
                .createdAt(LocalDateTime.now()).build();
    }

    private Comment comment(Long id, Long authorId, Long parentId) {
        return Comment.builder().id(id).postId(10L).userId(authorId).parentId(parentId).content("댓글 " + id)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void 상세에서_차단한_사용자의_댓글과_그_답글은_보이지_않는다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post(10L, 2L)));
        when(blockService.getBlockedIds(1L)).thenReturn(Set.of(3L));
        when(commentRepository.findByPostIdAndDeletedAtIsNullAndHiddenAtIsNullOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                comment(100L, 3L, null),     // 차단한 사람의 댓글
                comment(101L, 4L, 100L),     // 그 댓글에 달린 답글
                comment(102L, 5L, null),     // 정상 댓글
                comment(103L, 3L, 102L),     // 차단한 사람의 답글
                comment(104L, 6L, 102L)));   // 정상 답글

        PostDetailResponse detail = service.getPost(10L, 1L, "u1");

        assertEquals(List.of(102L, 104L), detail.getComments().stream().map(c -> c.getId()).toList());
    }

    @Test
    void 차단하지_않았으면_댓글이_모두_보인다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post(10L, 2L)));
        when(blockService.getBlockedIds(null)).thenReturn(Set.of());
        when(commentRepository.findByPostIdAndDeletedAtIsNullAndHiddenAtIsNullOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(comment(100L, 3L, null), comment(101L, 4L, 100L)));

        assertEquals(2, service.getPost(10L, null, "ip1").getComments().size());
    }

    @Test
    void 차단한_사용자의_게시글_상세는_열_수_없다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post(10L, 2L)));
        when(blockService.getBlockedIds(1L)).thenReturn(Set.of(2L));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.getPost(10L, 1L, "u1"));
        assertEquals("차단한 사용자의 게시글입니다.", e.getMessage());
    }

    @Test
    void 신고로_숨겨진_게시글_상세는_열_수_없다() {
        Post hidden = post(10L, 2L);
        hidden.setHiddenAt(LocalDateTime.now());
        when(postRepository.findById(10L)).thenReturn(Optional.of(hidden));

        assertThrows(IllegalStateException.class, () -> service.getPost(10L, 1L, "u1"));
    }

    @Test
    void 목록은_차단한_사용자를_빼고_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        when(blockService.getBlockedIds(1L)).thenReturn(Set.of(3L, 4L));
        when(postRepository.findByFilters(any(), any(), any(), any(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<PostListResponse> result = service.getPosts(null, null, null, pageable, 1L);

        assertEquals(0, result.getTotalElements());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> excluded = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(postRepository).findByFilters(any(), any(), any(), excluded.capture(), eq(pageable));
        assertEquals(Set.of(3L, 4L), Set.copyOf(excluded.getValue()));
    }

    @Test
    void 차단한_사람이_없거나_로그인하지_않아도_제외_목록은_비어있지_않다() {
        Pageable pageable = PageRequest.of(0, 20);
        when(blockService.getBlockedIds(null)).thenReturn(Set.of());
        when(postRepository.findByFilters(any(), any(), any(), any(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        service.getPosts(null, null, null, pageable, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> excluded = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(postRepository).findByFilters(any(), any(), any(), excluded.capture(), eq(pageable));
        // 빈 목록을 NOT IN 에 넣으면 쿼리가 깨질 수 있어서 존재하지 않는 id 하나를 넣는다
        assertEquals(List.of(-1L), List.copyOf(excluded.getValue()));
    }

    @Test
    void 검색어는_앞뒤_공백을_자르고_공백뿐이면_검색하지_않는다() {
        Pageable pageable = PageRequest.of(0, 20);
        when(blockService.getBlockedIds(null)).thenReturn(Set.of());
        when(postRepository.findByFilters(any(), any(), any(), any(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        service.getPosts(null, null, "  동행 ", pageable, null);
        service.getPosts(null, null, "   ", pageable, null);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(postRepository, org.mockito.Mockito.times(2)).findByFilters(any(), any(), keyword.capture(), any(), eq(pageable));
        assertEquals("동행", keyword.getAllValues().get(0));
        assertEquals(null, keyword.getAllValues().get(1));
    }

    @Test
    void 욕설이_들어간_글은_저장하지_않는다() {
        PostCreateRequest request = new PostCreateRequest();
        request.setCategory("free");
        request.setTitle("제목");
        request.setContent("이 공연 시발 별로");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createPost(1L, request));

        assertEquals(ProfanityFilter.MESSAGE, e.getMessage());
        org.mockito.Mockito.verify(postRepository, org.mockito.Mockito.never()).save(any());
    }
}
