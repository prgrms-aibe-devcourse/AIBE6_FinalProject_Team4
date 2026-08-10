package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.dto.response.PlantGrowthContextResponse;

/** 다른 도메인이 식물 관리 컨텍스트를 조회할 때 사용하는 content 도메인의 공개 경계. */
public interface PlantGrowthContextQuery {
	PlantGrowthContextResponse getGrowthContext(Long userId, Long profileId, int recentJournalLimit);
}
