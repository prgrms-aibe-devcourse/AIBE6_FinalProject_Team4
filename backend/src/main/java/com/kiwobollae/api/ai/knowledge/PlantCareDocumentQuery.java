package com.kiwobollae.api.ai.knowledge;

/** 코퍼스 구현체가 검색에 사용하는 정규화된 종명이다. */
public record PlantCareDocumentQuery(String requestedSpeciesName, String speciesLookupKey) {}
