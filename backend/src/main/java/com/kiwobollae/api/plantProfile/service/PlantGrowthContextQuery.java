package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;

/** 다른 도메인이 식물 관리 컨텍스트를 조회할 때 사용하는 공개 경계. */
public interface PlantGrowthContextQuery {
	void verifyOwnership(Long userId, Long profileId);

	/** 일반 식물 화면용 최근 일지 컨텍스트를 최대 10건으로 조회한다. */
	PlantGrowthContextResponse getRecentGrowthContext(Long userId, Long profileId);

	/** 과거 회상형 챗봇 질문을 위한 일지 이력 컨텍스트를 최대 500건으로 조회한다. */
	PlantGrowthContextResponse getJournalHistoryContext(Long userId, Long profileId);
}
