package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_highlight_cache")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class ReviewHighlightCache {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    private Long targetId;
    private int reviewCountAtGeneration;
    private String keywordsJson;
    private LocalDateTime updatedAt;

    public enum TargetType {
        festival, venue
    }
}
