package com.kiwobollae.api.plantProfile.dto.response;

import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record PlantProfileResponse(
		Long id,
		Long userId,
		Long speciesId,
		String speciesName,
		String careGuide,
		String nickname,
		LocalDate startDate,
		String thumbnailUrl,
		PlantStatus status,
		LocalDateTime createdAt,
		boolean journalRewardGrantedToday
) {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static PlantProfileResponse from(PlantProfile plantProfile) {
		LocalDateTime grantedAt = plantProfile.getJournalRewardGrantedAt();
		// journalRewardGrantedAt은 "마지막으로 보상 받은 시각"일 뿐 매일 자동으로 null로
		// 리셋되지 않는다(claimJournalReward가 호출 시점에 오늘 이전인지 조건부로만 판정) —
		// 그래서 응답 시점에 그 값이 KST 기준 오늘 날짜인지 직접 비교해야 "오늘 지급됨"이 된다.
		boolean journalRewardGrantedToday = grantedAt != null && grantedAt.toLocalDate().isEqual(LocalDate.now(KST));
		return new PlantProfileResponse(
				plantProfile.getId(),
				plantProfile.getUser().getId(),
				plantProfile.getSpecies().getId(),
				plantProfile.getSpecies().getName(),
				plantProfile.getSpecies().getCareGuide(),
				plantProfile.getPlantName(),
				plantProfile.getStartDate(),
				plantProfile.getPlantImage(),
				plantProfile.getStatus(),
				plantProfile.getCreatedAt(),
				journalRewardGrantedToday
		);
	}
}
