package com.pickstage.backend.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserPrivacyTest {

    @Test
    void 기본값은_모두_공개() {
        UserPrivacy privacy = UserPrivacy.defaultsFor(1L);
        assertTrue(privacy.isShowBookmarks());
        assertTrue(privacy.isShowArtists());
        assertTrue(privacy.isShowPosts());
        assertTrue(privacy.isShowReviews());
    }

    @Test
    void 값을_보낸_항목만_바뀐다() {
        UserPrivacy privacy = UserPrivacy.defaultsFor(1L);
        privacy.update(false, null, null, false);
        assertFalse(privacy.isShowBookmarks());
        assertTrue(privacy.isShowArtists());
        assertTrue(privacy.isShowPosts());
        assertFalse(privacy.isShowReviews());

        privacy.update(true, null, null, null);
        assertTrue(privacy.isShowBookmarks());
        assertFalse(privacy.isShowReviews());
    }
}
