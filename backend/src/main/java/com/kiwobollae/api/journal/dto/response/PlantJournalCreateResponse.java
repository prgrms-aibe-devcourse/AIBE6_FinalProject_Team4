package com.kiwobollae.api.journal.dto.response;

import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservation;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import java.util.List;

public record PlantJournalCreateResponse(
		PlantJournalResponse journal,
		boolean rewardGranted,
		long rewardAmount
) {
	public static PlantJournalCreateResponse from(
			PlantJournal journal,
			List<JournalImage> images,
			boolean rewardGranted,
			long rewardAmount,
			GachaRewardReservation gachaReservation
	) {
		return new PlantJournalCreateResponse(
				PlantJournalResponse.from(
						journal,
						images,
						GachaRewardResponse.from(gachaReservation, rewardGranted)
				),
				rewardGranted,
				rewardAmount
		);
	}
}
