package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.entity.PlantJournal;
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
			long rewardAmount
	) {
		return new PlantJournalCreateResponse(
				PlantJournalResponse.from(journal, images),
				rewardGranted,
				rewardAmount
		);
	}
}
