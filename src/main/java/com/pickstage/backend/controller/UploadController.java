package com.pickstage.backend.controller;

import com.pickstage.backend.dto.PresignedUrlRequest;
import com.pickstage.backend.dto.PresignedUrlResponse;
import com.pickstage.backend.global.response.ApiResponse;
import com.pickstage.backend.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final S3Service s3Service;

    public UploadController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    // 업로드용 presigned URL 발급
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestBody PresignedUrlRequest request, @AuthenticationPrincipal Long userId) {

        PresignedUrlResponse result = s3Service.generatePresignedUrl(request.getFileName());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
