package com.kiwobollae.api.ai.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 정규화한 종명과 별칭으로 공식 문서 코퍼스를 검색한다. */
@Component
@RequiredArgsConstructor
public class OfficialDocumentPlantCareKnowledgeRetriever implements PlantCareKnowledgeRetriever {

  private static final String RETRIEVER_VERSION = "official-document-retriever-v1";

  private final PlantSpeciesNameNormalizer speciesNameNormalizer;
  private final PlantCareDocumentCorpus documentCorpus;

  @Override
  public PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("재배 근거 검색 조건이 필요합니다.");
    }
    PlantSpeciesNameNormalizer.NormalizedSpeciesName normalized =
        speciesNameNormalizer.normalize(query.speciesName());
    PlantCareDocumentSearchResult searchResult =
        documentCorpus.search(
            new PlantCareDocumentQuery(normalized.cacheName(), normalized.lookupKey()));
    String retrievalVersion = RETRIEVER_VERSION + ":" + searchResult.corpusVersion();
    if (searchResult.evidence().isEmpty()) {
      return PlantCareKnowledge.fallback(
          normalized.cacheName(),
          searchResult.resolvedSpeciesName(),
          searchResult.matchType(),
          retrievalVersion);
    }
    return PlantCareKnowledge.verified(
        normalized.cacheName(),
        searchResult.resolvedSpeciesName(),
        searchResult.matchType(),
        retrievalVersion,
        searchResult.evidence());
  }
}
