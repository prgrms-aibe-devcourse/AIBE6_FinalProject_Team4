package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantGrowthContextService implements PlantGrowthContextQuery {

	private static final int MAX_JOURNAL_CONTENT_CHAR_BUDGET = 20_000;
	private static final int JOURNAL_CONTEXT_FETCH_LIMIT = 100;

	private final PlantProfileRepository plantProfileRepository;
	private final PlantJournalRepository plantJournalRepository;

	@Override
	public PlantGrowthContextResponse getGrowthContext(Long userId, Long profileId, int journalContentCharBudget) {
		validateIds(userId, profileId);
		if (journalContentCharBudget < 1 || journalContentCharBudget > MAX_JOURNAL_CONTENT_CHAR_BUDGET) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "일지 컨텍스트 문자 예산이 올바르지 않습니다.");
		}

		// 타인의 프로필도 같은 404로 응답해 프로필 존재 여부를 노출하지 않는다.
		PlantProfile profile = plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		List<PlantJournal> recentJournals = plantJournalRepository.findRecentActiveByProfile(
				userId, profileId, PageRequest.of(0, JOURNAL_CONTEXT_FETCH_LIMIT));

		return PlantGrowthContextResponse.from(profile, journalsWithinContentBudget(recentJournals, journalContentCharBudget));
	}

	/** 최신순으로 일지 본문 전체를 유지하면서 AI에 전달할 문자 예산을 넘지 않게 자른다. */
	private List<PlantJournal> journalsWithinContentBudget(List<PlantJournal> journals, int charBudget) {
		List<PlantJournal> selected = new ArrayList<>();
		int consumed = 0;
		for (PlantJournal journal : journals) {
			int contentLength = journal.getContent() == null ? 0 : journal.getContent().length();
			if (consumed + contentLength > charBudget) {
				break;
			}
			selected.add(journal);
			consumed += contentLength;
		}
		return List.copyOf(selected);
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
