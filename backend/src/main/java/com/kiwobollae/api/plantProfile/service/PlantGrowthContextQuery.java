package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;

/** 다른 도메인이 식물 관리 컨텍스트를 조회할 때 사용하는 공개 경계. */
public interface PlantGrowthContextQuery {
	PlantGrowthContextResponse getGrowthContext(Long userId, Long profileId, int recentJournalLimit);
}
