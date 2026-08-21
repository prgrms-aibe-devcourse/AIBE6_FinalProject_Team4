package com.kiwobollae.api.ai.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 자유 입력 종명에 대해 검색된 공식 문서 근거와 fallback 상태를 함께 보존한다. */
public record PlantCareKnowledge(
    String requestedSpeciesName,
    String resolvedSpeciesName,
    PlantCareSpeciesMatchType speciesMatchType,
    PlantCareEvidenceStatus evidenceStatus,
    String retrievalVersion,
    List<PlantCareEvidence> evidence) {

  public PlantCareKnowledge {
    if (blank(requestedSpeciesName)
        || blank(resolvedSpeciesName)
        || speciesMatchType == null
        || evidenceStatus == null
        || blank(retrievalVersion)
        || evidence == null) {
      throw new IllegalArgumentException("재배 근거 조회 결과가 올바르지 않습니다.");
    }
    evidence =
        evidence.stream()
            .sorted(java.util.Comparator.comparing(PlantCareEvidence::sourceId))
            .toList();
    if (evidenceStatus == PlantCareEvidenceStatus.VERIFIED && evidence.isEmpty()) {
      throw new IllegalArgumentException("VERIFIED 근거에는 공식 문서가 필요합니다.");
    }
    if (evidenceStatus == PlantCareEvidenceStatus.GENERAL_FALLBACK && !evidence.isEmpty()) {
      throw new IllegalArgumentException("GENERAL_FALLBACK에는 공식 문서를 포함할 수 없습니다.");
    }
  }

  public static PlantCareKnowledge verified(
      String requestedSpeciesName,
      String resolvedSpeciesName,
      String retrievalVersion,
      List<PlantCareEvidence> evidence) {
    return verified(
        requestedSpeciesName,
        resolvedSpeciesName,
        PlantCareSpeciesMatchType.CANONICAL_NAME,
        retrievalVersion,
        evidence);
  }

  public static PlantCareKnowledge verified(
      String requestedSpeciesName,
      String resolvedSpeciesName,
      PlantCareSpeciesMatchType speciesMatchType,
      String retrievalVersion,
      List<PlantCareEvidence> evidence) {
    return new PlantCareKnowledge(
        requestedSpeciesName,
        resolvedSpeciesName,
        speciesMatchType,
        PlantCareEvidenceStatus.VERIFIED,
        retrievalVersion,
        evidence);
  }

  public static PlantCareKnowledge fallback(
      String requestedSpeciesName, String resolvedSpeciesName, String retrievalVersion) {
    return fallback(
        requestedSpeciesName,
        resolvedSpeciesName,
        PlantCareSpeciesMatchType.NONE,
        retrievalVersion);
  }

  public static PlantCareKnowledge fallback(
      String requestedSpeciesName,
      String resolvedSpeciesName,
      PlantCareSpeciesMatchType speciesMatchType,
      String retrievalVersion) {
    return new PlantCareKnowledge(
        requestedSpeciesName,
        resolvedSpeciesName,
        speciesMatchType,
        PlantCareEvidenceStatus.GENERAL_FALLBACK,
        retrievalVersion,
        List.of());
  }

  public boolean isVerified() {
    return evidenceStatus == PlantCareEvidenceStatus.VERIFIED;
  }

  public String sourceContextHash() {
    return PlantCareHash.sha256(fingerprintMaterial());
  }

  public PlantCareGrounding grounding() {
    return new PlantCareGrounding(
        evidenceStatus,
        evidenceScope(),
        resolvedSpeciesName,
        evidence.stream().map(PlantCareEvidence::source).toList());
  }

  public PlantCareEvidenceScope evidenceScope() {
    if (!isVerified()) {
      return PlantCareEvidenceScope.NONE;
    }
    return speciesMatchType == PlantCareSpeciesMatchType.CULTIVAR
        ? PlantCareEvidenceScope.BASE_SPECIES
        : PlantCareEvidenceScope.EXACT_SPECIES;
  }

  /** 모델에게 전달하는 데이터다. fallback 안전 정책도 모든 AI 기능에서 같은 문구를 사용한다. */
  public Map<String, Object> promptPayload() {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("evidenceStatus", evidenceStatus.name());
    context.put("evidenceScope", evidenceScope().name());
    context.put("speciesMatchType", speciesMatchType.name());
    context.put("resolvedSpeciesName", resolvedSpeciesName);
    if (evidenceScope() == PlantCareEvidenceScope.BASE_SPECIES) {
      context.put(
          "scopeSafetyPolicy",
          List.of(
              "공식 근거는 입력 품종 전용 자료가 아니라 기준 작물의 공통 재배 자료입니다.",
              "품종 고유의 생육 특성·수확량·병해 저항성·정확한 재배 수치를 공식 근거처럼 단정하지 않습니다."));
    }
    if (isVerified()) {
      context.put(
          "officialSources",
          evidence.stream()
              .map(
                  item -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("sourceId", item.sourceId());
                    source.put("sourceName", item.sourceName());
                    source.put("sourceUrl", item.sourceUrl());
                    source.put("version", item.version());
                    source.put("contentHash", item.contentHash());
                    source.put("content", item.content());
                    return source;
                  })
              .toList());
    } else {
      context.put("officialSources", List.of());
      context.put(
          "fallbackSafetyPolicy",
          List.of(
              "공식 문서로 검증되지 않은 일반 AI 지식임을 전제로 보수적으로 안내합니다.",
              "정확한 투입량·희석 배수·처리 주기 같은 수치를 처방하지 않습니다.",
              "농약·살충제·살균제·비료 제품이나 성분을 처방하지 않습니다.",
              "관찰 항목과 제품 표시사항·공공기관·전문가 확인 방법을 우선 안내합니다."));
    }
    return context;
  }

  private String fingerprintMaterial() {
    StringBuilder material = new StringBuilder();
    material
        .append(retrievalVersion)
        .append('\n')
        .append(evidenceStatus.name())
        .append('\n')
        .append(resolvedSpeciesName)
        .append('\n');
    for (PlantCareEvidence item : evidence) {
      String itemMaterial = item.fingerprintMaterial();
      material.append(itemMaterial.length()).append('\n').append(itemMaterial).append('\n');
    }
    return material.toString();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
