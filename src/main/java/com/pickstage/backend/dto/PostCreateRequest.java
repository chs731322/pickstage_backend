package com.pickstage.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCreateRequest {
    private Long festivalId;
    private String category;
    private String title;
    private String content;
}
