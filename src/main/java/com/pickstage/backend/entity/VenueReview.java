package com.pickstage.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "venue_reviews")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VenueReview {
    
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long venueId;
    private int rating;
    private String content;
    private String viewInfo;
    private String standingTip;
    private String seatSection;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    // 신고가 쌓여 임시로 숨긴 시각
    private LocalDateTime hiddenAt;
}
