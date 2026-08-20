package com.kiwobollae.api.ai.guide.knowledge;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 선택한 식물 종에 정확히 결속된 검증 재배 자료를 모은다.
 *
 * <p>종명 필터 없이 유사한 식물 자료를 섞지 않는다. 향후 벡터 검색을 도입하더라도 이 정확 일치 범위를 먼저 적용한 뒤에만 후보를 확장해야 한다.
 */
@Component
public class VerifiedPlantCareKnowledgeRetriever implements PlantCareKnowledgeRetriever {

  private final ClasspathPlantCareKnowledgeCatalog catalog;

  public VerifiedPlantCareKnowledgeRetriever(ClasspathPlantCareKnowledgeCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query) {
    if (query == null) {
      return new PlantCareKnowledge(List.of());
    }
    return new PlantCareKnowledge(catalog.findBySpeciesName(query.speciesName()));
  }
}
