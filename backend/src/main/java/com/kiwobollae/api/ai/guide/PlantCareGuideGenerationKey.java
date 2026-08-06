package com.kiwobollae.api.ai.guide;

/** 종별 재배 가이드 캐시와 동일한 생성 선점 키. */
public record PlantCareGuideGenerationKey(
    String speciesName, int guideVersion, String sourceContextHash) {}
