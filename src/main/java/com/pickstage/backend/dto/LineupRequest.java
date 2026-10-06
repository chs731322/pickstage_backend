package com.pickstage.backend.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LineupRequest {
    private String artistName;
    private LocalDate performanceDate;
}
