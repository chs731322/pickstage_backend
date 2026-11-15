package com.pickstage.backend.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VenueImageInfo {

    private Long id;
    private String imageUrl;
    // imagetype enum('exterior','interior','view','standing')
    private String imageType;
    private int sortOrder;
    // 사진 출처 표기 (없으면 null)
    private String credit;
}
