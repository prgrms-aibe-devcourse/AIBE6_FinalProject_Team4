package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantGrowthContextService implements PlantGrowthContextQuery {

	private static final int RECENT_JOURNAL_LIMIT = 10;
	private static final int MAX_JOURNAL_HISTORY_FETCH_LIMIT = 500;

	private final PlantProfileRepository plantProfileRepository;
	private final PlantJournalRepository plantJournalRepository;

	@Override
	public void verifyOwnership(Long userId, Long profileId) {
		validateIds(userId, profileId);
		if (!plantProfileRepository.existsByIdAndUserId(profileId, userId)) {
			throw new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND);
		}
	}

	@Override
	public PlantGrowthContextResponse getRecentGrowthContext(Long userId, Long profileId) {
		return loadGrowthContext(userId, profileId, RECENT_JOURNAL_LIMIT);
	}

	@Override
	public PlantGrowthContextResponse getJournalHistoryContext(Long userId, Long profileId) {
		return loadGrowthContext(userId, profileId, MAX_JOURNAL_HISTORY_FETCH_LIMIT);
	}

	private PlantGrowthContextResponse loadGrowthContext(Long userId, Long profileId, int journalFetchLimit) {
		validateIds(userId, profileId);

		// 타인의 프로필도 같은 404로 응답해 프로필 존재 여부를 노출하지 않는다.
		PlantProfile profile = plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		List<PlantJournal> recentJournals = plantJournalRepository.findRecentActiveByProfile(
				userId, profileId, PageRequest.of(0, journalFetchLimit));

		return PlantGrowthContextResponse.from(profile, recentJournals);
	}

	private void validateIds(Long userId, Long profileId) {
		if (userId == null || userId < 1) {
			throw new IllegalArgumentException("식물 컨텍스트 조회 사용자 ID가 필요합니다.");
		}
		if (profileId == null || profileId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "식물 프로필 ID가 필요합니다.");
		}
	}
}
