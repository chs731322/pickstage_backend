package com.pickstage.backend.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI가 만든 공연 등록 초안. 관리자가 검토/수정한 뒤 기존 공연 등록 API(POST /api/admin/festivals)로 저장한다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FestivalDraftResponse {

    private Draft draft;
    private List<VenueCandidate> venueCandidates;
    private List<DuplicateCandidate> duplicates;
    private List<ArtistMatch> artistMatches;
    private List<String> warnings;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Draft {
        private String title;
        private String description;
        private Long venueId; // 이름이 정확히 일치하는 공연장이 있을 때만 채움
        private String venueName;
        private String address;
        private String region;
        private LocalDate startDate;
        private LocalDate endDate;
        private String genre;
        private List<TicketDraft> tickets;
        private List<LineupRequest> lineup;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketDraft {
        private String label;
        private String vendorName;
        private Integer price;
        private String bookingUrl;
        private java.time.LocalDateTime openAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueCandidate {
        private Long id;
        private String name;
        private String address;
        private int score;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateCandidate {
        private Long id;
        private String title;
        private LocalDate startDate;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtistMatch {
        private String name;
        private Long artistId; // null이면 신규 아티스트로 등록됨
        private String matchedName;
        private boolean newArtist;
    }
}
