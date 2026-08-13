package com.kiwobollae.api.journal.dto.response;

import java.time.LocalDate;
import java.util.List;

/** AI 도메인이 저장된 성장일지 사진을 분석할 때 사용하는 content 도메인의 읽기 전용 계약이다. */
public record JournalImageAnalysisContext(
		Long journalId,
		Long plantProfileId,
		String plantNickname,
		String speciesName,
		String speciesCategory,
		String officialCareGuide,
		LocalDate writtenDate,
		String journalContent,
		List<Image> images,
		List<RecentJournal> recentJournals
) {
	public JournalImageAnalysisContext {
		images = images == null ? List.of() : List.copyOf(images);
		recentJournals = recentJournals == null ? List.of() : List.copyOf(recentJournals);
	}

	public record Image(String imageUrl, String imageHash, boolean representative) {}

	public record RecentJournal(LocalDate writtenDate, String content) {}
}
