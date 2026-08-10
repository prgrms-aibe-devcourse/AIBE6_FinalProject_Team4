package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.util.List;

/** 식물 관리 조언에 필요한 content 도메인의 읽기 전용 컨텍스트. */
public record PlantGrowthContextResponse(
		Long profileId,
		String nickname,
		LocalDate startDate,
		PlantStatus status,
		Long speciesId,
		String speciesName,
		String speciesCategory,
		String officialCareGuide,
		List<RecentJournal> recentJournals
) {
	public PlantGrowthContextResponse {
		recentJournals = recentJournals == null ? List.of() : List.copyOf(recentJournals);
	}

	public static PlantGrowthContextResponse from(PlantProfile profile, List<PlantJournal> journals) {
		PlantSpecies species = profile.getSpecies();
		return new PlantGrowthContextResponse(
				profile.getId(),
				profile.getPlantName(),
				profile.getStartDate(),
				profile.getStatus(),
				species.getId(),
				species.getName(),
				species.getCategory(),
				species.getCareGuide(),
				journals.stream().map(RecentJournal::from).toList()
		);
	}

	public record RecentJournal(Long journalId, LocalDate writtenDate, String content) {
		private static RecentJournal from(PlantJournal journal) {
			return new RecentJournal(journal.getId(), journal.getWrittenDate(), journal.getContent());
		}
	}
}
