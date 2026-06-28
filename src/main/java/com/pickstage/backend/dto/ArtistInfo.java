package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistInfo {
    private Long id;
    private String name;
    private String profileImage;
    private boolean isHeadliner;
    private LocalDate performanceDate;
}