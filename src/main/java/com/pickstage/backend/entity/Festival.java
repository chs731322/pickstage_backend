package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "festivals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Festival {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "festival_id")
    private Long id;

    private String title;
    private String description;

    @Column(name = "venue_name")
    private String venueName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    private String address;
    private String region;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "ticket_open_date")
    private LocalDateTime ticketOpenDate;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(name = "booking_url")
    private String bookingUrl;

    private String genre;

    @Builder.Default
    @Column(name = "view_count")
    private int viewCount = 0;

    @Builder.Default
    @Column(name = "bookmark_count")
    private int bookmarkCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementBookmarkCount() {
        this.bookmarkCount++;
    }

    public void decrementBookmarkCount() {
        this.bookmarkCount--;
    }
}
