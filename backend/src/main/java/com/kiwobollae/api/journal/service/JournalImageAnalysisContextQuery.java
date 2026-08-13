package com.kiwobollae.api.journal.service;

import com.kiwobollae.api.journal.dto.response.JournalImageAnalysisContext;

/** AI 사진 분석이 content 내부 저장소에 직접 접근하지 않고 일지 컨텍스트를 조회하는 공개 경계다. */
public interface JournalImageAnalysisContextQuery {

	JournalImageAnalysisContext getAnalysisContext(Long userId, Long journalId, int recentJournalLimit);
}
