package com.kiwobollae.api.ai.knowledge;

import java.util.List;

/** AI 응답에 붙는 서버 검증 근거 요약이다. */
public record PlantCareGrounding(
    PlantCareEvidenceStatus status,
    PlantCareEvidenceScope scope,
    String resolvedSpeciesName,
    List<PlantCareEvidenceSource> sources) {

  public PlantCareGrounding {
    if (status == null || scope == null || sources == null) {
      throw new IllegalArgumentException("재배 근거 상태와 출처가 필요합니다.");
    }
    sources = List.copyOf(sources);
    if (status == PlantCareEvidenceStatus.VERIFIED
        && (scope == PlantCareEvidenceScope.NONE
            || resolvedSpeciesName == null
            || resolvedSpeciesName.isBlank()
            || sources.isEmpty())) {
      throw new IllegalArgumentException("검증 근거 상태에는 출처가 필요합니다.");
    }
    if (status == PlantCareEvidenceStatus.GENERAL_FALLBACK
        && (scope != PlantCareEvidenceScope.NONE || !sources.isEmpty())) {
      throw new IllegalArgumentException("일반 지식 fallback에는 검증 출처를 표시할 수 없습니다.");
    }
  }

  public static PlantCareGrounding fallback() {
    return fallback(null);
  }

  public static PlantCareGrounding fallback(String resolvedSpeciesName) {
    return new PlantCareGrounding(
        PlantCareEvidenceStatus.GENERAL_FALLBACK,
        PlantCareEvidenceScope.NONE,
        resolvedSpeciesName,
        List.of());
  }
}
