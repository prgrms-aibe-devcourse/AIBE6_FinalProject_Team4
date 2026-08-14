package com.kiwobollae.api.journal.service;

import com.kiwobollae.api.journal.dto.response.JournalImageAnalysisContext;

/** AI 사진 분석이 content 내부 저장소에 직접 접근하지 않고 일지 컨텍스트를 조회하는 공개 경계다. */
public interface JournalImageAnalysisContextQuery {

	/** 소유한 활성 일지에 요청 사진이 현재 연결되어 있는지만 가볍게 검증한다. */
	void validateAnalysisTarget(Long userId, Long journalId, String imageHash);

	JournalImageAnalysisContext getAnalysisContext(Long userId, Long journalId, int recentJournalLimit);
}
