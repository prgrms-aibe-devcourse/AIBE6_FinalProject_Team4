package com.kiwobollae.api.ai.knowledge;

/** 공식 문서 검색으로 찾은 재배 근거 한 건이다. */
public record PlantCareEvidence(
    String sourceId,
    String sourceName,
    String sourceUrl,
    String version,
    String content,
    String contentHash) {

  public PlantCareEvidence(
      String sourceId, String sourceName, String sourceUrl, String version, String content) {
    this(sourceId, sourceName, sourceUrl, version, content, hash(content));
  }

  public PlantCareEvidence {
    if (blank(sourceId)
        || blank(sourceName)
        || blank(sourceUrl)
        || blank(version)
        || blank(content)
        || contentHash == null
        || !contentHash.equals(hash(content))) {
      throw new IllegalArgumentException("재배 근거의 출처, 버전, 내용과 내용 해시가 필요합니다.");
    }
    // API가 이 값을 링크로 노출하므로 코퍼스 로딩 시점에도 HTTPS 출처인지 검증한다.
    new PlantCareEvidenceSource(sourceId, sourceName, sourceUrl, version, contentHash);
  }

  public PlantCareEvidenceSource source() {
    return new PlantCareEvidenceSource(sourceId, sourceName, sourceUrl, version, contentHash);
  }

  String fingerprintMaterial() {
    return sourceId + "\n" + sourceName + "\n" + sourceUrl + "\n" + version + "\n" + contentHash;
  }

  private static String hash(String content) {
    return PlantCareHash.sha256(content == null ? "" : content);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
