package com.pickstage.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.pickstage.backend.dto.PresignedUrlResponse;

@Service
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    public S3Service(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    // 허용하는 이미지 형식과 파일 확장자
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    // 업로드 크기 상한 5MB
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    // 업로드용 presigned URL 발급 (이미지 형식·크기를 서명에 고정해서 다른 파일은 S3가 거부)
    public PresignedUrlResponse generatePresignedUrl(String contentType, Long size) {
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new IllegalArgumentException("JPG, PNG, WEBP, GIF 이미지만 올릴 수 있습니다.");
        }
        if (size == null || size <= 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException("이미지는 5MB 이하만 올릴 수 있습니다.");
        }

        // 원본 파일명은 쓰지 않고 확장자도 형식에서 정한다
        String key = UUID.randomUUID() + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType.toLowerCase())
                .contentLength(size)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String fileUrl = String.format(
                "https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);

        return PresignedUrlResponse.builder()
                .uploadUrl(presignedRequest.url().toString())
                .fileUrl(fileUrl)
                .build();
    }
}
