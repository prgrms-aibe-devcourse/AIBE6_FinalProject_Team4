package com.kiwobollae.api.ai.knowledge;

/** 재배가이드·챗봇·사진분석이 함께 사용하는 공식 문서 근거 조회 계약이다. */
public interface PlantCareKnowledgeRetriever {

  PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query);
}
