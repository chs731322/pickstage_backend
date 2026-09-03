package com.pickstage.backend.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TicketRequest {
    private String label;
    private String vendorName;
    private Integer price;
    private String bookingUrl;
    private LocalDateTime openAt;
}
