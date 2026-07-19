package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "festival_images")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id")
    private Festival festival;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder;
}
