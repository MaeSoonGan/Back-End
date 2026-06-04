package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.auth.AuthDtos.ProfileImageUploadUrlResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ProfileImageService {

    private static final String KEY_PREFIX = "profile-images/";

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String region;
    private final long expirySeconds;

    public ProfileImageService(
            S3Presigner s3Presigner,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.presigned-url-expiry-seconds}") long expirySeconds
    ) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.region = region;
        this.expirySeconds = expirySeconds;
    }

    public ProfileImageUploadUrlResponse createUploadUrl(long memberId, String contentType) {
        String extension = extensionOf(contentType);
        String key = KEY_PREFIX + memberId + "/" + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new ProfileImageUploadUrlResponse(presigned.url().toString(), publicUrl(key));
    }

    private String publicUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }
}
