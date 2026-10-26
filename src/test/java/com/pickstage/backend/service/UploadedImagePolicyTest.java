package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UploadedImagePolicyTest {

    private final UploadedImagePolicy policy = new UploadedImagePolicy("my-bucket", "ap-northeast-2");

    @Test
    void 우리_버킷_주소만_허용한다() {
        assertTrue(policy.isUploadedImageUrl("https://my-bucket.s3.ap-northeast-2.amazonaws.com/abc.jpg"));
    }

    @Test
    void 다른_사이트_주소는_거절한다() {
        assertFalse(policy.isUploadedImageUrl("https://evil.example/pixel.gif"));
        assertFalse(policy.isUploadedImageUrl("http://my-bucket.s3.ap-northeast-2.amazonaws.com/abc.jpg"));
        // 접두사처럼 보이지만 호스트가 다른 주소
        assertFalse(policy.isUploadedImageUrl("https://my-bucket.s3.ap-northeast-2.amazonaws.com.evil.example/a.jpg"));
        assertFalse(policy.isUploadedImageUrl("https://my-bucket.s3.ap-northeast-2.amazonaws.com@evil.example/a.jpg"));
    }

    @Test
    void 빈_값과_공백_제어문자가_섞인_주소는_거절한다() {
        assertFalse(policy.isUploadedImageUrl(null));
        assertFalse(policy.isUploadedImageUrl(""));
        assertFalse(policy.isUploadedImageUrl("https://my-bucket.s3.ap-northeast-2.amazonaws.com/"));
        assertFalse(policy.isUploadedImageUrl("https://my-bucket.s3.ap-northeast-2.amazonaws.com/a b.jpg"));
    }
}
