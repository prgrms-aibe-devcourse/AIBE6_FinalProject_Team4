package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class CommerceAssetStorageService {

  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(".jpg", ".jpeg", ".png", ".webp");
  private static final Set<String> ALLOWED_PREFIXES = Set.of("products", "coupons");

  private final S3Client s3Client;

  @Value("${aws.s3.bucket}")
  private String bucket;

  public String upload(MultipartFile file, String prefix, Long resourceId) {
    if (!ALLOWED_PREFIXES.contains(prefix) || resourceId == null || resourceId < 1) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    if (file == null
        || file.isEmpty()
        || file.getSize() > MAX_FILE_SIZE
        || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new BusinessException(ErrorCode.COMMERCE_IMAGE_INVALID);
    }

    String extension = extensionOf(file).toLowerCase(Locale.ROOT);
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BusinessException(ErrorCode.COMMERCE_IMAGE_INVALID);
    }

    String key = prefix + "/" + resourceId + "/" + UUID.randomUUID() + extension;
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(file.getContentType())
              .build(),
          RequestBody.fromBytes(file.getBytes()));
      return key;
    } catch (SdkException | IOException exception) {
      throw new BusinessException(ErrorCode.COMMERCE_IMAGE_UPLOAD_FAILED);
    }
  }

  private String extensionOf(MultipartFile file) {
    String original = file.getOriginalFilename();
    int dot = original == null ? -1 : original.lastIndexOf('.');
    return dot >= 0 ? original.substring(dot) : "";
  }
}
