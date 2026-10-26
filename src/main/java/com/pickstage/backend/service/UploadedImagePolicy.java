package com.pickstage.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 사용자가 올린 이미지(우리 S3 버킷) 주소인지 확인한다.
// 다른 사이트의 이미지를 걸어 두면 그 이미지를 보는 사람의 IP·브라우저 정보가 그 사이트에 남기 때문에, 우리 저장소 주소만 허용한다.
@Component
public class UploadedImagePolicy {

    private final String allowedPrefix;

    public UploadedImagePolicy(
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region}") String region) {
        this.allowedPrefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    public boolean isUploadedImageUrl(String url) {
        return url != null
                && url.startsWith(allowedPrefix)
                && url.length() > allowedPrefix.length()
                && url.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
    }
}
