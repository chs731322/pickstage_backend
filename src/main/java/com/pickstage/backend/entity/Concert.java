package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private LocalDate startDate;   // DATE → LocalDate
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;           // venueId → Venue 엔티티

    private String region;
    private String genre;
    private String posterImage;
    private String description;

    private LocalDateTime ticketOpenAt;
    private String ticketLink;
    private String externalUrl;    // ExternalLink → externalUrl

    @Enumerated(EnumType.STRING)   // ENUM 어노테이션 추가
    private Source source;

    public enum Source {
        kopis, crawl, admin
    }

    private String sourceId;
    private boolean isFestival;

    @Builder.Default
    private int viewCount = 0;

    @Builder.Default
    private int bookmarkCount = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}