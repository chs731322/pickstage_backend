package com.pickstage.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "venue_images")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VenueImage {
    
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private Long venueId;
    private String imageUrl;
    private String imageType;
    private int sortOrder;
    private LocalDateTime createdAt;
    // 사진 출처 표기 (작성자 · 라이선스). CC BY 같은 라이선스 사진에는 화면에 꼭 보여줘야 한다
    private String credit;
    
}
