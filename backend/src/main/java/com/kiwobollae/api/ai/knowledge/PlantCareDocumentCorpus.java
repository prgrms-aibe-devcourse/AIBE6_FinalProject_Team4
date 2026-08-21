package com.kiwobollae.api.ai.knowledge;

/** 공식 문서 저장 위치와 검색 기술을 교체할 수 있게 분리한 코퍼스 계약이다. */
public interface PlantCareDocumentCorpus {

  PlantCareDocumentSearchResult search(PlantCareDocumentQuery query);
}
