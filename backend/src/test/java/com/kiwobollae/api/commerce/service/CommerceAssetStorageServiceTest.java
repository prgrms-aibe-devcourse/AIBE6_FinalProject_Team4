package com.kiwobollae.api.commerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class CommerceAssetStorageServiceTest {

  @Mock private S3Client s3Client;

  private CommerceAssetStorageService service;

  @BeforeEach
  void setUp() {
    service = new CommerceAssetStorageService(s3Client);
    ReflectionTestUtils.setField(service, "bucket", "test-bucket");
  }

  @Test
  void uploadsProductImageWithResourceIdAndUuidKey() {
    MockMultipartFile file =
        new MockMultipartFile("file", "seedling.png", "image/png", "image".getBytes());

    String key = service.upload(file, "products", 17L);

    assertThat(key)
        .matches(
            "products/17/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
    assertThat(request.getValue().key()).isEqualTo(key);
    assertThat(request.getValue().contentType()).isEqualTo("image/png");
  }

  @Test
  void rejectsUnsupportedImageType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "script.svg", "image/svg+xml", "svg".getBytes());

    assertThatThrownBy(() -> service.upload(file, "coupons", 3L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.COMMERCE_IMAGE_INVALID);
  }

  @Test
  void rejectsUnknownStoragePrefix() {
    MockMultipartFile file =
        new MockMultipartFile("file", "coupon.webp", "image/webp", "image".getBytes());

    assertThatThrownBy(() -> service.upload(file, "other", 3L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
  }
}
