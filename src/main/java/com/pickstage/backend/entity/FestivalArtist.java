package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "festival_artists")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalArtist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "festival_artist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id")
    private Festival festival;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Column(name = "is_headliner")
    private boolean isHeadliner;

    @Column(name = "performance_date")
    private LocalDate performanceDate;

    // 라인업 표기 순서
    @Column(name = "billing_order")
    private Integer billingOrder;

    // 등록 경로 (admin / excel_lineup 등)
    private String source;
}
