package com.pickstage.backend.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueInfo {
    private Long id;
    private String name;
    private String address;
    private String region;
    private BigDecimal latitude;
    private BigDecimal longitude;
}