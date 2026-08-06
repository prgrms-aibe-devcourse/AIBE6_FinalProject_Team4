package com.kiwobollae.api.ai.guide.dto;

/**
 * 관리자 캐시 무효화 결과.
 *
 * <p>{@code deletedCount}가 0이면 지울 저장본이 없었다는 뜻이다 — 오류가 아니라 "이미 비어 있다"는 정상 결과이므로, 관리자가 헛짚었는지 알 수 있게
 * 개수를 그대로 내려준다.
 */
public record PlantCareGuideInvalidation(String speciesName, long deletedCount) {}
