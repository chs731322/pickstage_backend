package com.pickstage.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketInfo {
    private String label;
    private String vendorName;
    private Integer price;
    private String bookingUrl;
    private LocalDateTime openAt;
}
