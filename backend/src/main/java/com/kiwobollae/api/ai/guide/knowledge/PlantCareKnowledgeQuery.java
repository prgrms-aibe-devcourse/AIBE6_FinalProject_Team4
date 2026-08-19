package com.kiwobollae.api.ai.guide.knowledge;

import java.time.LocalDateTime;

/** 콘텐츠 도메인이 공개한 식물 종 정보를 재배 근거 조회에 전달하는 값 객체. */
public record PlantCareKnowledgeQuery(
    Long speciesId,
    String speciesName,
    String category,
    String officialGuide,
    LocalDateTime sourceUpdatedAt) {}
