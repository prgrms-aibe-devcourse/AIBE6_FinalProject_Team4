package com.kiwobollae.api.ai.knowledge;

import java.util.List;

/** 공식 문서 코퍼스가 반환하는 종명 매칭과 문서 근거다. */
public record PlantCareDocumentSearchResult(
    String resolvedSpeciesName,
    PlantCareSpeciesMatchType matchType,
    String corpusVersion,
    List<PlantCareEvidence> evidence) {

  public PlantCareDocumentSearchResult {
    if (resolvedSpeciesName == null
        || resolvedSpeciesName.isBlank()
        || matchType == null
        || corpusVersion == null
        || corpusVersion.isBlank()
        || evidence == null) {
      throw new IllegalArgumentException("공식 문서 검색 결과가 올바르지 않습니다.");
    }
    evidence = List.copyOf(evidence);
  }
}
