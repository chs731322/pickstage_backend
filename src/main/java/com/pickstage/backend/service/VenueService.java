package com.pickstage.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.pickstage.backend.repository.VenueRepository;
import com.pickstage.backend.repository.VenueImageRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.dto.VenueDetailResponse;
import com.pickstage.backend.dto.VenueImageInfo;
import com.pickstage.backend.dto.VenueListResponse;
import com.pickstage.backend.dto.VenueReviewInfo;
import com.pickstage.backend.dto.VenueUpcomingConcertInfo;
import com.pickstage.backend.entity.Venue;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.VenueReviewRepository;
import com.pickstage.backend.repository.ReviewImageRepository;
import com.pickstage.backend.entity.ReviewImage;

@Service
public class VenueService {
    /*
     * 1. venueRepository.findById(id) → 없으면
     * IllegalStateException("존재하지 않는 공연장입니다.") (404)
     * 2. venueImageRepository.findByVenueIdOrderBySortOrderAsc(id) →
     * VenueImageInfo로 변환
     * 3. festivalRepository.findByVenueIdAndStartDateGreaterThanEqual(id,
     * LocalDate.now()) → 작은 DTO로 변환
     * 4. venueReviewRepository.findByVenueIdAndDeletedAtIsNull(id) →
     * VenueReviewInfo로 변환 (userRepository로 닉네임 조회)
     * 5. 넷 다 합쳐서 VenueDetailResponse 빌드
     */

    private final VenueRepository venueRepository;
    private final VenueImageRepository venueImageRepository;
    private final FestivalRepository festivalRepository;
    private final VenueReviewRepository venueReviewRepository;
    private final UserRepository userRepository;
    private final ReviewImageRepository reviewImageRepository;

    public VenueService(VenueRepository venueRepository, VenueImageRepository venueImageRepository,
            FestivalRepository festivalRepository, VenueReviewRepository venueReviewRepository,
            UserRepository userRepository, ReviewImageRepository reviewImageRepository) {
        this.venueRepository = venueRepository;
        this.venueImageRepository = venueImageRepository;
        this.festivalRepository = festivalRepository;
        this.venueReviewRepository = venueReviewRepository;
        this.userRepository = userRepository;
        this.reviewImageRepository = reviewImageRepository;
    }

    // 공연장 목록 조회
    public PageResponse<VenueListResponse> getVenues(
            String region, String keyword, String sort, int page, int size) {

        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("올바르지 않은 요청입니다.");
        }

        Page<Venue> venues;

        if ("popular".equals(sort)) {
            Pageable pageable = PageRequest.of(page, size);
            venues = venueRepository.findByFiltersOrderByReviewCountDesc(region, keyword, pageable);
        } else {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            venues = venueRepository.findByFilters(region, keyword, pageable);
        }

        Page<VenueListResponse> content = venues.map(venue -> VenueListResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .region(venue.getRegion())
                .image(venueImageRepository.findFirstByVenueIdOrderBySortOrderAsc(venue.getId())
                        .map(image -> image.getImageUrl())
                        .orElse(null))
                .build());

        return PageResponse.of(content);
    }

    // 공연장 상세 조회
    public VenueDetailResponse getVenueDetail(Long id) {
        var venue = venueRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 공연장입니다."));
        var images = venueImageRepository.findByVenueIdOrderBySortOrderAsc(id).stream()
                .map(image -> VenueImageInfo.builder()
                        .id(image.getId())
                        .imageUrl(image.getImageUrl())
                        .imageType(image.getImageType())
                        .sortOrder(image.getSortOrder())
                        .credit(image.getCredit())
                        .build())
                .toList();
        var upcomingConcerts = festivalRepository.findByVenueIdAndStartDateGreaterThanEqual(id, java.time.LocalDate.now()).stream()
                .map(festival -> VenueUpcomingConcertInfo.builder()
                        .id(festival.getId())
                        .title(festival.getTitle())
                        .startDate(festival.getStartDate())
                        .build())
                .toList();
        var reviews = venueReviewRepository.findByVenueIdAndDeletedAtIsNull(id).stream()
                .map(review -> VenueReviewInfo.builder()
                        .id(review.getId())
                        .nickname(userRepository.findById(review.getUserId())
                                .map(com.pickstage.backend.entity.User::getNickname)
                                .orElse(null))
                        .rating(review.getRating())
                        .content(review.getContent())
                        .viewInfo(review.getViewInfo())
                        .standingTip(review.getStandingTip())
                        .seatSection(review.getSeatSection())
                        .createdAt(review.getCreatedAt())
                        .images(reviewImageRepository.findByReviewIdOrderBySortOrderAsc(review.getId()).stream()
                                .map(ReviewImage::getImageUrl)
                                .toList())
                        .build())
                .toList();
        return VenueDetailResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .region(venue.getRegion())
                .latitude(venue.getLatitude())
                .longitude(venue.getLongitude())
                .capacity(venue.getCapacity())
                .transportInfo(venue.getTransportInfo())
                .facilities(venue.getFacilities())
                .images(images)
                .upcomingConcerts(upcomingConcerts)
                .reviews(reviews)
                .build();
    }
}
