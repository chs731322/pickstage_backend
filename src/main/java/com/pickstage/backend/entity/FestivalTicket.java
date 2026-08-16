package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "festival_tickets")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id")
    private Festival festival;

    private String label;

    @Column(name = "vendor_name")
    private String vendorName;

    private Integer price;

    @Column(name = "booking_url")
    private String bookingUrl;

    @Column(name = "open_at")
    private LocalDateTime openAt;

    @Column(name = "sort_order")
    private int sortOrder;
}
