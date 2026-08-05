package com.kiwobollae.api.ai.client;

import com.kiwobollae.api.ai.config.AiImageProperties;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class AiImageUrlResolver {

  private static final String JOURNAL_IMAGE_PATH_PREFIX = ApiVersion.V1 + "/journals/images/";

  private final AiImageProperties properties;

  // 설정 값은 런타임에 바뀌지 않으므로 첫 사용 때 한 번만 검증해 재사용한다. 생성자에서 검증하면
  // 값이 없을 때 애플리케이션이 기동하지 못하는데, 텍스트 기능은 이 값 없이도 동작해야 한다.
  private volatile URI cachedPublicBaseUrl;

  public AiImageUrlResolver(AiImageProperties properties) {
    this.properties = properties;
  }

  public String resolveJournalImageUrl(String storedImageUrl) {
    URI imagePath = parseJournalImagePath(storedImageUrl);
    return publicBaseUrl().resolve(imagePath).toString();
  }

  private URI publicBaseUrl() {
    URI cached = cachedPublicBaseUrl;
    if (cached == null) {
      cached = parsePublicBaseUrl();
      cachedPublicBaseUrl = cached;
    }
    return cached;
  }

  private URI parseJournalImagePath(String storedImageUrl) {
    if (storedImageUrl == null || storedImageUrl.isBlank()) {
      throw invalidImagePath();
    }

    URI imagePath;
    try {
      imagePath = URI.create(storedImageUrl);
    } catch (IllegalArgumentException exception) {
      throw invalidImagePath();
    }

    String rawPath = imagePath.getRawPath();
    if (imagePath.isAbsolute()
        || imagePath.getRawAuthority() != null
        || imagePath.getRawQuery() != null
        || imagePath.getRawFragment() != null
        || rawPath == null
        || rawPath.contains("%")
        || !rawPath.startsWith(JOURNAL_IMAGE_PATH_PREFIX)
        || !rawPath.equals(imagePath.normalize().getRawPath())
        || !hasOwnerAndFilename(rawPath)) {
      throw invalidImagePath();
    }
    return imagePath;
  }

  private boolean hasOwnerAndFilename(String rawPath) {
    String remainder = rawPath.substring(JOURNAL_IMAGE_PATH_PREFIX.length());
    int separator = remainder.indexOf('/');
    if (separator <= 0
        || separator == remainder.length() - 1
        || remainder.indexOf('/', separator + 1) >= 0) {
      return false;
    }
    String ownerId = remainder.substring(0, separator);
    return ownerId.chars().allMatch(Character::isDigit);
  }

  private URI parsePublicBaseUrl() {
    String configuredUrl = properties.publicBaseUrl();
    if (configuredUrl == null || configuredUrl.isBlank()) {
      throw new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);
    }

    URI baseUrl;
    try {
      baseUrl = URI.create(configuredUrl);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);
    }

    String rawPath = baseUrl.getRawPath();
    if (!baseUrl.isAbsolute()
        // URI.getScheme()은 대소문자를 보존한다("HTTPS://..." → "HTTPS").
        || !"https".equalsIgnoreCase(baseUrl.getScheme())
        || baseUrl.getHost() == null
        || baseUrl.getUserInfo() != null
        || baseUrl.getRawQuery() != null
        || baseUrl.getRawFragment() != null
        || (rawPath != null && !rawPath.isEmpty() && !"/".equals(rawPath))) {
      throw new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);
    }
    return baseUrl.resolve("/");
  }

  private IllegalArgumentException invalidImagePath() {
    return new IllegalArgumentException("AI 일지 이미지 경로가 올바르지 않습니다.");
  }
}
