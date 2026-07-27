package com.kiwobollae.api.content.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.dto.request.JournalImageRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalRequest;
import com.kiwobollae.api.content.dto.response.PlantJournalResponse;
import com.kiwobollae.api.content.entity.JournalCompletionLog;
import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalCompletionLogRepository;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantJournalService {

	// 작성일·하루 경계는 KST 기준으로 판정한다 (중복검사·완료 판정의 "같은 날" 기준).
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;
	private final JournalCompletionLogRepository journalCompletionLogRepository;
	private final PlantProfileRepository plantProfileRepository;
	private final UserRepository userRepository;

	@Transactional
	public PlantJournalResponse createJournal(Long userId, PlantJournalRequest request) {
		PlantProfile profile = plantProfileRepository.findByIdAndUserId(request.plantProfileId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		validateRepresentative(request.images());

		LocalDate today = LocalDate.now(KST);
		checkDuplicateImages(userId, request.images(), today);

		User user = userRepository.getReferenceById(userId);
		PlantJournal journal = plantJournalRepository.save(
				PlantJournal.create(user, profile, request.content(), today));
		List<JournalImage> images = request.images().stream()
				.map(img -> JournalImage.create(journal, user, img.imageUrl(), img.imageHash(),
						img.representative(), today))
				.toList();
		journalImageRepository.saveAll(images);

		// 작성완료 체크(1식물 1회): 이 프로필에 완료 로그가 없을 때만 생성한다.
		// 완료 로그는 point 도메인이 읽어 실제 포인트를 지급하는 트리거이며, 여기서는 기록만 남긴다.
		if (!journalCompletionLogRepository.existsByProfileId(profile.getId())) {
			journalCompletionLogRepository.save(JournalCompletionLog.create(user, profile, journal, today));
		}
		return PlantJournalResponse.from(journal, images);
	}

	// 대표 이미지는 정확히 1장이어야 한다.
	private void validateRepresentative(List<JournalImageRequest> images) {
		long representativeCount = images.stream().filter(JournalImageRequest::representative).count();
		if (representativeCount != 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "대표 이미지는 정확히 1장이어야 합니다.");
		}
	}

	// 같은 날 동일 사진 재사용 차단: 요청 내부 중복과 기존 저장분과의 중복을 모두 검사한다.
	private void checkDuplicateImages(Long userId, List<JournalImageRequest> images, LocalDate writtenDate) {
		Set<String> seenHashes = new HashSet<>();
		for (JournalImageRequest image : images) {
			boolean duplicatedInRequest = !seenHashes.add(image.imageHash());
			if (duplicatedInRequest
					|| journalImageRepository.existsDuplicate(userId, image.imageHash(), writtenDate)) {
				throw new BusinessException(ErrorCode.JOURNAL_DUPLICATE_IMAGE);
			}
		}
	}
}
