package com.kiwobollae.api.journal.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.dto.response.JournalImageAnalysisContext;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.species.entity.PlantSpecies;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalImageAnalysisContextService implements JournalImageAnalysisContextQuery {

	private static final int MAX_RECENT_JOURNAL_LIMIT = 10;

	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;

	@Override
	public void validateAnalysisTarget(Long userId, Long journalId, String imageHash) {
		validateIdentifiers(userId, journalId);
		if (imageHash == null || imageHash.isBlank()
				|| !journalImageRepository.existsOwnedActiveImage(userId, journalId, imageHash)) {
			throw new BusinessException(ErrorCode.AI_IMAGE_ANALYSIS_IMAGE_NOT_FOUND);
		}
	}

	@Override
	public JournalImageAnalysisContext getAnalysisContext(
			Long userId,
			Long journalId,
			int recentJournalLimit
	) {
		validateIdentifiers(userId, journalId);
		if (recentJournalLimit < 1 || recentJournalLimit > MAX_RECENT_JOURNAL_LIMIT) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		List<JournalImage> images = journalImageRepository.findByJournalId(journalId);
		PlantProfile profile = journal.getPlantProfile();
		PlantSpecies species = profile.getSpecies();
		List<PlantJournal> recentJournals = plantJournalRepository.findRecentActiveByProfileExcluding(
				userId,
				profile.getId(),
				journalId,
				PageRequest.of(0, recentJournalLimit)
		);

		return new JournalImageAnalysisContext(
				journal.getId(),
				profile.getId(),
				profile.getPlantName(),
				species.getName(),
				species.getCategory(),
				species.getCareGuide(),
				journal.getWrittenDate(),
				journal.getContent(),
				images.stream()
						.map(image -> new JournalImageAnalysisContext.Image(
								image.getImageUrl(), image.getImageHash(), image.isRepresentative()))
						.toList(),
				recentJournals.stream()
						.map(recent -> new JournalImageAnalysisContext.RecentJournal(
								recent.getWrittenDate(), recent.getContent()))
						.toList()
		);
	}

	private void validateIdentifiers(Long userId, Long journalId) {
		if (userId == null || userId < 1) {
			throw new IllegalArgumentException("AI 사진 분석 사용자 ID가 필요합니다.");
		}
		if (journalId == null || journalId < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}
}
