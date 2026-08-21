package com.kiwobollae.api.ai.knowledge;

import java.net.URI;
import java.net.URISyntaxException;

/** 사용자에게 공개하고 생성 결과와 함께 저장할 공식 문서 출처 메타데이터다. */
public record PlantCareEvidenceSource(
    String sourceId, String sourceName, String sourceUrl, String version, String contentHash) {

  public PlantCareEvidenceSource {
    if (blank(sourceId)
        || blank(sourceName)
        || blank(sourceUrl)
        || !isSecureWebUrl(sourceUrl)
        || blank(version)
        || contentHash == null
        || !contentHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("재배 근거 출처 메타데이터가 올바르지 않습니다.");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isSecureWebUrl(String value) {
    try {
      URI uri = new URI(value);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    } catch (URISyntaxException exception) {
      return false;
    }
  }
}
