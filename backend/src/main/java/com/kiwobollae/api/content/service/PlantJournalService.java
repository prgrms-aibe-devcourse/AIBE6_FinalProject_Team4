package com.kiwobollae.api.content.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.dto.request.JournalImageRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalUpdateRequest;
import com.kiwobollae.api.content.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.content.dto.response.PlantJournalResponse;
import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.JournalRewardResult;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
	private final PlantProfileRepository plantProfileRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final JournalImageUploadService journalImageUploadService;

	@Transactional
	public PlantJournalCreateResponse createJournal(Long userId, PlantJournalRequest request) {
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

		// 작성완료 체크(1식물 1일 1회, 매일 리셋): 오늘 아직 지급 안 됐을 때만 원자적으로 클레임하고,
		// 클레임에 성공한 경우에만 point 도메인에 실제 지급을 요청한다(동시 요청 중복 지급 방지).
		LocalDateTime now = LocalDateTime.now(KST);
		LocalDateTime startOfToday = today.atStartOfDay();
		boolean rewardGranted =
				plantProfileRepository.claimJournalReward(profile.getId(), now, startOfToday) == 1;
		long rewardAmount = 0L;
		if (rewardGranted) {
			JournalRewardResult rewardResult = walletService.rewardJournal(userId, journal.getId());
			rewardAmount = rewardResult.rewardAmount();
		}
		return PlantJournalCreateResponse.from(
				journal,
				images,
				rewardGranted,
				rewardAmount
		);
	}

	public Page<PlantJournalResponse> getJournals(Long userId, Long profileId, Integer year, Integer month,
			Pageable pageable) {
		DateRange range = resolveDateRange(year, month);
		Page<PlantJournal> journalPage =
				plantJournalRepository.search(userId, profileId, range.start(), range.end(), pageable);
		Map<Long, List<JournalImage>> imagesByJournal = loadImagesByJournal(journalPage.getContent());
		return journalPage.map(journal ->
				PlantJournalResponse.from(journal, imagesByJournal.getOrDefault(journal.getId(), List.of())));
	}

	public boolean existsActive(Long journalId) {
		return plantJournalRepository.existsByIdAndDeletedAtIsNull(journalId);
	}

	public PlantJournalResponse getJournal(Long userId, Long journalId) {
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		return PlantJournalResponse.from(journal, journalImageRepository.findByJournalId(journalId));
	}

	@Transactional
	public PlantJournalResponse updateJournal(Long userId, Long journalId, PlantJournalUpdateRequest request) {
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		validateRepresentative(request.images());
		journal.updateContent(request.content());

		// 이미지 전체 교체: 기존 이미지를 먼저 지우고(같은 사진 유지 시 자기 자신과의 중복 오탐 방지),
		// 원래 작성일 기준으로 중복검사한 뒤 새 이미지를 저장한다. 사진은 수정 불가가 아니라 전체 교체한다.
		List<JournalImage> oldImages = journalImageRepository.findByJournalId(journalId);
		journalImageRepository.deleteByJournalId(journalId);
		LocalDate writtenDate = journal.getWrittenDate();
		checkDuplicateImages(userId, request.images(), writtenDate);

		User user = userRepository.getReferenceById(userId);
		List<JournalImage> images = request.images().stream()
				.map(img -> JournalImage.create(journal, user, img.imageUrl(), img.imageHash(),
						img.representative(), writtenDate))
				.toList();
		journalImageRepository.saveAll(images);

		// 새 목록에 그대로 남아있는 사진(교체 안 한 경우)의 S3 객체까지 지우면 안 되므로,
		// 실제로 빠진 것만 골라서 정리한다.
		Set<String> keptUrls = request.images().stream().map(JournalImageRequest::imageUrl).collect(Collectors.toSet());
		oldImages.stream()
				.map(JournalImage::getImageUrl)
				.filter(url -> !keptUrls.contains(url))
				.forEach(url -> journalImageUploadService.delete(url, userId));

		return PlantJournalResponse.from(journal, images);
	}

	@Transactional
	public void deleteJournal(Long userId, Long journalId) {
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		List<JournalImage> images = journalImageRepository.findByJournalId(journalId);
		journal.softDelete(LocalDateTime.now(KST));
		// 작성 보상은 삭제 여부와 무관하게 확정 지급한다. 당일 클레임도 유지하므로
		// 삭제 후 같은 식물 프로필로 다시 작성해도 당일 추가 보상은 지급되지 않는다.

		// soft delete는 사용자에게만 "삭제됨"으로 보일 뿐 복구 API가 없어 사실상 영구 삭제와
		// 같으므로, 더 이상 어떤 일지도 참조하지 않는 S3 객체를 이 시점에 정리한다.
		images.forEach(image -> journalImageUploadService.delete(image.getImageUrl(), userId));
	}

	// 페이지에 담긴 일지들의 이미지를 한 번에 로딩해 journalId로 묶는다 (개별 조회로 인한 N+1 방지).
	private Map<Long, List<JournalImage>> loadImagesByJournal(List<PlantJournal> journals) {
		if (journals.isEmpty()) {
			return Map.of();
		}
		List<Long> journalIds = journals.stream().map(PlantJournal::getId).toList();
		return journalImageRepository.findByJournalIdIn(journalIds).stream()
				.collect(Collectors.groupingBy(image -> image.getJournal().getId()));
	}

	// 날짜 필터를 조회 기간으로 변환한다. year 없으면 전체, year만 있으면 그 해, year+month면 그 달.
	private DateRange resolveDateRange(Integer year, Integer month) {
		if (year == null) {
			if (month != null) {
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "월 필터는 연도와 함께 사용해야 합니다.");
			}
			return new DateRange(null, null);
		}
		if (year < 1970 || year > 9999) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "연도가 올바르지 않습니다.");
		}
		if (month == null) {
			return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
		}
		if (month < 1 || month > 12) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "월은 1~12 사이여야 합니다.");
		}
		LocalDate start = LocalDate.of(year, month, 1);
		return new DateRange(start, start.withDayOfMonth(start.lengthOfMonth()));
	}

	private record DateRange(LocalDate start, LocalDate end) {
	}

	// 대표 이미지는 정확히 1장이어야 한다.
	private void validateRepresentative(List<JournalImageRequest> images) {
		long representativeCount = images.stream().filter(JournalImageRequest::representative).count();
		if (representativeCount != 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "대표 이미지는 정확히 1장이어야 합니다.");
		}
	}

	private void checkDuplicateImages(Long userId, List<JournalImageRequest> images, LocalDate writtenDate) {
		List<String> hashes = images.stream().map(JournalImageRequest::imageHash).toList();
		boolean duplicatedInRequest = new HashSet<>(hashes).size() != hashes.size();
		// 건당 조회 대신 해시 리스트를 한 번에 IN 조회해 이미 저장된 것이 있는지 확인한다.
		boolean duplicatedInStorage = !journalImageRepository.findExistingHashes(userId, hashes, writtenDate).isEmpty();
		if (duplicatedInRequest || duplicatedInStorage) {
			throw new BusinessException(ErrorCode.JOURNAL_DUPLICATE_IMAGE);
		}
	}
}
