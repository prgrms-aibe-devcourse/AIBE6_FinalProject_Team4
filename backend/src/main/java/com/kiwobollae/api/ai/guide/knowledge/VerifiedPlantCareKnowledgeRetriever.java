package com.kiwobollae.api.ai.guide.knowledge;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 선택한 식물 종에 정확히 결속된 서비스 등록 가이드와 검증 자료를 모은다.
 *
 * <p>종명 필터 없이 유사한 식물 자료를 섞지 않는다. 향후 벡터 검색을 도입하더라도 이 정확 일치 범위를 먼저 적용한 뒤에만 후보를 확장해야 한다.
 */
@Component
public class VerifiedPlantCareKnowledgeRetriever implements PlantCareKnowledgeRetriever {

  private static final String REGISTERED_GUIDE_NAME = "서비스 등록 공식 재배 가이드";
  private static final String FALLBACK_VERSION = "등록일 미상";

  private final ClasspathPlantCareKnowledgeCatalog catalog;

  public VerifiedPlantCareKnowledgeRetriever(ClasspathPlantCareKnowledgeCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query) {
    if (query == null) {
      return new PlantCareKnowledge(List.of());
    }

    List<PlantCareEvidence> evidence = new ArrayList<>();
    registeredOfficialGuide(query).ifPresent(evidence::add);
    evidence.addAll(catalog.findBySpeciesName(query.speciesName()));
    return new PlantCareKnowledge(evidence);
  }

  private java.util.Optional<PlantCareEvidence> registeredOfficialGuide(
      PlantCareKnowledgeQuery query) {
    if (query.officialGuide() == null || query.officialGuide().isBlank()) {
      return java.util.Optional.empty();
    }
    String sourceId =
        query.speciesId() == null
            ? "plant-species:unknown:official-care-guide"
            : "plant-species:" + query.speciesId() + ":official-care-guide";
    String sourceUrl =
        query.speciesId() == null
            ? "internal://plant-species/unknown/care-guide"
            : "internal://plant-species/" + query.speciesId() + "/care-guide";
    return java.util.Optional.of(
        new PlantCareEvidence(
            sourceId,
            REGISTERED_GUIDE_NAME,
            sourceUrl,
            formatVersion(query.sourceUpdatedAt()),
            query.officialGuide().trim()));
  }

  private String formatVersion(LocalDateTime sourceUpdatedAt) {
    if (sourceUpdatedAt == null) {
      return FALLBACK_VERSION;
    }
    return sourceUpdatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }
}
