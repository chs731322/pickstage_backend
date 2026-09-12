package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import com.pickstage.backend.dto.ReviewHighlightResponse;
import com.pickstage.backend.dto.ReviewKeywordHighlight;
import com.pickstage.backend.entity.Post;
import com.pickstage.backend.entity.ReviewHighlightCache;
import com.pickstage.backend.entity.VenueReview;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.PostRepository;
import com.pickstage.backend.repository.ReviewHighlightCacheRepository;
import com.pickstage.backend.repository.VenueRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

@Service
public class ReviewHighlightService {

    private static final int MIN_REVIEW_COUNT = 3;

    private final PostRepository postRepository;
    private final VenueReviewRepository venueReviewRepository;
    private final FestivalRepository festivalRepository;
    private final VenueRepository venueRepository;
    private final ReviewHighlightCacheRepository cacheRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public ReviewHighlightService(PostRepository postRepository, VenueReviewRepository venueReviewRepository,
            FestivalRepository festivalRepository, VenueRepository venueRepository,
            ReviewHighlightCacheRepository cacheRepository, GeminiService geminiService,
            ObjectMapper objectMapper) {
        this.postRepository = postRepository;
        this.venueReviewRepository = venueReviewRepository;
        this.festivalRepository = festivalRepository;
        this.venueRepository = venueRepository;
        this.cacheRepository = cacheRepository;
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    // 공연 후기 하이라이트 조회
    @Transactional
    public ReviewHighlightResponse getFestivalHighlights(Long festivalId) {
        var festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연입니다."));

        List<Post> reviews = postRepository.findByCategoryAndFestivalIdAndDeletedAtIsNull(
                Post.Category.review, festivalId);
        List<String> texts = reviews.stream().map(p -> p.getTitle() + "\n" + p.getContent()).toList();

        return getOrGenerate(ReviewHighlightCache.TargetType.festival, festivalId, festival.getTitle(), texts);
    }

    // 공연장 후기 하이라이트 조회
    @Transactional
    public ReviewHighlightResponse getVenueHighlights(Long venueId) {
        var venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연장입니다."));

        List<VenueReview> reviews = venueReviewRepository.findByVenueIdAndDeletedAtIsNull(venueId);
        List<String> texts = reviews.stream().map(VenueReview::getContent).toList();

        return getOrGenerate(ReviewHighlightCache.TargetType.venue, venueId, venue.getName(), texts);
    }

    // 캐시 확인 후 없거나 오래됐으면 AI로 재생성
    private ReviewHighlightResponse getOrGenerate(ReviewHighlightCache.TargetType targetType, Long targetId,
            String targetLabel, List<String> reviewTexts) {

        int reviewCount = reviewTexts.size();

        if (reviewCount < MIN_REVIEW_COUNT) {
            return ReviewHighlightResponse.builder()
                    .reviewCount(reviewCount)
                    .keywords(Collections.emptyList())
                    .updatedAt(null)
                    .build();
        }

        ReviewHighlightCache cache = cacheRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .orElse(null);

        if (cache == null || cache.getReviewCountAtGeneration() != reviewCount) {
            List<ReviewKeywordHighlight> generated = geminiService.extractHighlights(targetLabel, reviewTexts);
            String keywordsJson = objectMapper.writeValueAsString(generated);

            if (cache == null) {
                cache = ReviewHighlightCache.builder()
                        .targetType(targetType)
                        .targetId(targetId)
                        .reviewCountAtGeneration(reviewCount)
                        .keywordsJson(keywordsJson)
                        .updatedAt(LocalDateTime.now())
                        .build();
            } else {
                cache.setReviewCountAtGeneration(reviewCount);
                cache.setKeywordsJson(keywordsJson);
                cache.setUpdatedAt(LocalDateTime.now());
            }
            cacheRepository.save(cache);
        }

        List<ReviewKeywordHighlight> keywords = Arrays.asList(
                objectMapper.readValue(cache.getKeywordsJson(), ReviewKeywordHighlight[].class));

        return ReviewHighlightResponse.builder()
                .reviewCount(reviewCount)
                .keywords(keywords)
                .updatedAt(cache.getUpdatedAt())
                .build();
    }
}
