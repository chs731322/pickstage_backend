package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_id")
    private Long id;

    private String name;

    @Column(name = "genre_primary")
    private String genrePrimary;

    @Column(name = "profile_image_url")
    private String profileImageUrl;
}
