package com.pickstage.backend.dto;

import com.pickstage.backend.entity.UserPrivacy;

import lombok.Builder;
import lombok.Getter;

// 프로필의 항목별 공개 여부 (true = 공개)
@Getter
@Builder
public class PrivacySettings {

    private boolean showBookmarks;
    private boolean showArtists;
    private boolean showPosts;
    private boolean showReviews;

    public static PrivacySettings of(UserPrivacy privacy) {
        return PrivacySettings.builder()
                .showBookmarks(privacy.isShowBookmarks())
                .showArtists(privacy.isShowArtists())
                .showPosts(privacy.isShowPosts())
                .showReviews(privacy.isShowReviews())
                .build();
    }

    public static PrivacySettings allVisible() {
        return PrivacySettings.builder()
                .showBookmarks(true)
                .showArtists(true)
                .showPosts(true)
                .showReviews(true)
                .build();
    }
}
