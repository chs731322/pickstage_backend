package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 값이 있는 항목만 바꾼다
@Getter
@NoArgsConstructor
public class PrivacyUpdateRequest {

    private Boolean showBookmarks;
    private Boolean showArtists;
    private Boolean showPosts;
    private Boolean showReviews;
}
