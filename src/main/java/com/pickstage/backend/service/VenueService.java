package com.pickstage.backend.service;

import org.springframework.stereotype.Service;
import com.pickstage.backend.repository.VenueRepository;
import com.pickstage.backend.repository.VenueImageRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.dto.VenueDetailResponse;
import com.pickstage.backend.dto.VenueImageInfo;
import com.pickstage.backend.dto.VenueReviewInfo;
import com.pickstage.backend.dto.VenueUpcomingConcertInfo;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.VenueReviewRepository;

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
    
    public VenueService(VenueRepository venueRepository, VenueImageRepository venueImageRepository,
            FestivalRepository festivalRepository, VenueReviewRepository venueReviewRepository,
            UserRepository userRepository) {
        this.venueRepository = venueRepository;
        this.venueImageRepository = venueImageRepository;
        this.festivalRepository = festivalRepository;
        this.venueReviewRepository = venueReviewRepository;
        this.userRepository = userRepository;
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
