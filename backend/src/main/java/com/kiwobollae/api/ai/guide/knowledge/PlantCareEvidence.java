package com.kiwobollae.api.ai.guide.knowledge;

/**
 * 재배 가이드 생성에 쓸 검증된 근거 한 건.
 *
 * <p>근거의 원문과 식별자를 함께 보존해 생성 프롬프트와 캐시 무효화 기준이 같은 자료를 바라보게 한다.
 */
public record PlantCareEvidence(
    String sourceId, String sourceName, String sourceUrl, String version, String content) {

  public PlantCareEvidence {
    if (blank(sourceId)
        || blank(sourceName)
        || blank(sourceUrl)
        || blank(version)
        || blank(content)) {
      throw new IllegalArgumentException("재배 근거의 식별자, 이름, URL, 버전, 내용이 필요합니다.");
    }
  }

  String fingerprintMaterial() {
    return sourceId
        + "\n"
        + sourceName
        + "\n"
        + sourceUrl
        + "\n"
        + version
        + "\n"
        + content.length()
        + "\n"
        + content;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
