package com.pickstage.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 다른 사용자가 내 프로필에서 볼 수 있는 항목 설정. 행이 없으면 모두 공개로 본다.
@Entity
@Table(name = "user_privacy")
@Getter
@NoArgsConstructor
public class UserPrivacy {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "show_bookmarks", nullable = false)
    private boolean showBookmarks = true;

    @Column(name = "show_artists", nullable = false)
    private boolean showArtists = true;

    @Column(name = "show_posts", nullable = false)
    private boolean showPosts = true;

    @Column(name = "show_reviews", nullable = false)
    private boolean showReviews = true;

    public static UserPrivacy defaultsFor(Long userId) {
        UserPrivacy privacy = new UserPrivacy();
        privacy.userId = userId;
        return privacy;
    }

    // 값이 넘어온 항목만 바꾼다
    public void update(Boolean showBookmarks, Boolean showArtists, Boolean showPosts, Boolean showReviews) {
        if (showBookmarks != null) {
            this.showBookmarks = showBookmarks;
        }
        if (showArtists != null) {
            this.showArtists = showArtists;
        }
        if (showPosts != null) {
            this.showPosts = showPosts;
        }
        if (showReviews != null) {
            this.showReviews = showReviews;
        }
    }
}
