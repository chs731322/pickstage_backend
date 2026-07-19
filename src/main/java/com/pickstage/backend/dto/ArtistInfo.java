package com.pickstage.backend.dto;

import lombok.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistInfo {
    private Long id;
    private String name;
    private String profileImage;

    @Getter(onMethod_ = @__(@JsonProperty("isHeadliner")))
    private boolean isHeadliner;
    private LocalDate performanceDate;
}