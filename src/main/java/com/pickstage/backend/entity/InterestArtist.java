package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;    

@Entity
@Table(name = "interest_artists")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestArtist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}