package com.kiwobollae.api.ai.guide.knowledge;

/** 선택한 종에 대해서만 검증된 재배 근거를 조회한다. */
public interface PlantCareKnowledgeRetriever {

  PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query);
}
