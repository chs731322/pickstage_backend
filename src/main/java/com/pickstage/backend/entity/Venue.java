package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "venues")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String address;
    private String region;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int capacity;
    private String viewInfo;
    private String standingTip;

    @Column(columnDefinition = "json")
    private String facilities;
    private String transportInfo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}