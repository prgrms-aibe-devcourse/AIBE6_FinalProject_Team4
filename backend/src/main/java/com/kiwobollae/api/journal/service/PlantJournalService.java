package com.kiwobollae.api.journal.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservation;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservationService;
import com.kiwobollae.api.journal.dto.request.JournalImageRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalUpdateRequest;
import com.kiwobollae.api.journal.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.journal.dto.response.PlantJournalResponse;
import com.kiwobollae.api.journal.dto.response.DailyJournalRewardStatusResponse;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.entity.DailyJournalReward;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.DailyJournalRewardRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.plantProfile.service.PlantProfileService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.dto.response.JournalRewardResult;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

	private static final long JOURNAL_REWARD_AMOUNT = 100L;

	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;
	private final DailyJournalRewardRepository dailyJournalRewardRepository;
	private final PlantProfileRepository plantProfileRepository;
	private final PlantProfileService plantProfileService;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final GachaRewardReservationService gachaRewardReservationService;
	private final JournalImageUploadService journalImageUploadService;
	private final NotificationService notificationService;
	private final Clock seoulClock;

	@Transactional
	public PlantJournalCreateResponse createJournal(Long userId, PlantJournalRequest request) {
		PlantProfile profile = plantProfileRepository.findByIdAndUserId(request.plantProfileId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		validateRepresentative(request.images());

		LocalDate today = LocalDate.now(seoulClock);
		LocalDateTime now = LocalDateTime.now(seoulClock);
		checkDuplicateImages(userId, request.images(), today);

		User user = userRepository.getReferenceById(userId);
		PlantJournal journal = plantJournalRepository.saveAndFlush(
				PlantJournal.create(user, profile, request.content(), today, now));
		List<JournalImage> images = request.images().stream()
				.map(img -> JournalImage.create(journal, user, img.imageUrl(), img.imageHash(),
						img.representative(), today, now))
				.toList();
		journalImageRepository.saveAll(images);

		// 오늘의 사진 중 대표(★)로 고른 사진을 항상 식물 대표사진으로도 반영한다.
		String representativeImageUrl = images.stream()
				.filter(JournalImage::isRepresentative)
				.findFirst()
				.orElseThrow()
				.getImageUrl();
		plantProfileService.updateThumbnail(userId, profile, representativeImageUrl);

		// 계정·KST 날짜 유일 제약을 선점한 요청만 포인트·카드팩·알림을 같은 트랜잭션에서 처리한다.
		// journalId는 FK가 아닌 논리 참조라 일지 soft delete 뒤에도 보상 판정은 유지된다.
		GachaRewardReservation gachaReservation = GachaRewardReservation.none();
		dailyJournalRewardRepository.claim(userId, today, journal.getId(), JOURNAL_REWARD_AMOUNT, now);
		DailyJournalReward dailyReward = dailyJournalRewardRepository.findForUserAndRewardDate(userId, today)
				.orElseThrow(() -> new IllegalStateException("선점한 일일 일지 보상 기록을 찾을 수 없습니다."));
		// MySQL JDBC의 영향 행 수 설정은 ON DUPLICATE KEY UPDATE의 최초 삽입 여부를 보장하지 않는다.
		// 따라서 유일 키로 확정된 기록이 현재 일지를 가리킬 때만 실제 보상을 지급한다.
		boolean rewardGranted = Objects.equals(dailyReward.getJournalId(), journal.getId());
		long rewardAmount = 0L;
		if (rewardGranted) {
			JournalRewardResult rewardResult = walletService.rewardJournal(userId, journal.getId());
			rewardAmount = rewardResult.rewardAmount();
			gachaReservation = gachaRewardReservationService.reserveDailyJournalReward(userId, today);
			dailyReward.recordGachaDraw(gachaReservation.drawId());
			notificationService.notify(
					userId,
					NotificationType.POINT,
					"일지 작성 보너스 포인트가 지급됐어요",
					"일지 작성 보상 100P 지급",
					"/my/points",
					"DAILY_JOURNAL_REWARD",
					dailyReward.getId()
			);
		}
		return PlantJournalCreateResponse.from(
				journal,
				images,
				rewardGranted,
				rewardAmount,
				gachaReservation
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

	// "오늘 일지 안 쓴 것만 보기" 프론트 필터용 — 오늘(KST) 일지를 쓴 식물 프로필 id 목록만 가볍게 반환한다.
	public List<Long> getProfileIdsWrittenToday(Long userId) {
		return plantJournalRepository.findDistinctProfileIdsByUserIdAndWrittenDate(userId, LocalDate.now(seoulClock));
	}

	public DailyJournalRewardStatusResponse getDailyRewardStatus(Long userId) {
		return new DailyJournalRewardStatusResponse(
				dailyJournalRewardRepository.existsForUserAndRewardDate(userId, LocalDate.now(seoulClock))
		);
	}

	public boolean existsActive(Long journalId) {
		return plantJournalRepository.existsByIdAndDeletedAtIsNull(journalId);
	}

	public PlantJournalResponse getJournal(Long userId, Long journalId) {
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		return PlantJournalResponse.from(journal, journalImageRepository.findByJournalId(journalId));
	}

	// 게시판 PLANT_QNA 게시글에 연동된 일지를 작성자 본인이 아닌 열람자에게도 보여줄 때 쓴다.
	// 소유자 확인이 없으므로 호출부가 실제로 그 일지를 참조하는 활성 게시글 컨텍스트에서만
	// 호출해야 한다(BoardPostService.getLinkedJournal 참고).
	public PlantJournalResponse getPublicSnapshot(Long journalId) {
		PlantJournal journal = plantJournalRepository.findActive(journalId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		return PlantJournalResponse.from(journal, journalImageRepository.findByJournalId(journalId));
	}

	// 신고 검토 화면에서 관리자가 신고된 일지 원문을 확인할 때 쓴다. getPublicSnapshot과 달리
	// deletedAt을 걸러내지 않는다 — 작성자가 신고 접수 후 일지를 삭제해도 관리자는 신고 당시
	// 내용을 볼 수 있어야 한다(getPublicSnapshot은 공개 열람용이라 그대로 두고 별도 메서드로 뺀다).
	public PlantJournalResponse getForAdmin(Long journalId) {
		PlantJournal journal = plantJournalRepository.findById(journalId)
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
		LocalDateTime now = LocalDateTime.now(seoulClock);
		List<JournalImage> images = request.images().stream()
				.map(img -> JournalImage.create(journal, user, img.imageUrl(), img.imageHash(),
						img.representative(), writtenDate, now))
				.toList();
		journalImageRepository.saveAll(images);

		// 새 목록에 그대로 남아있는 사진(교체 안 한 경우)의 S3 객체까지 지우면 안 되므로,
		// 실제로 빠진 것만 골라서 정리한다.
		Set<String> keptUrls = request.images().stream().map(JournalImageRequest::imageUrl).collect(Collectors.toSet());
		oldImages.stream()
				.map(JournalImage::getImageUrl)
				.filter(url -> !keptUrls.contains(url))
				.forEach(url -> journalImageUploadService.delete(url, userId));

		// 수정 후 대표(★)로 고른 사진도 createJournal과 동일하게 항상 식물 대표사진으로 반영한다.
		String representativeImageUrl = images.stream()
				.filter(JournalImage::isRepresentative)
				.findFirst()
				.orElseThrow()
				.getImageUrl();
		plantProfileService.updateThumbnail(userId, journal.getPlantProfile(), representativeImageUrl);

		return PlantJournalResponse.from(journal, images);
	}

	@Transactional
	public void deleteJournal(Long userId, Long journalId) {
		PlantJournal journal = plantJournalRepository.findOwnedActive(journalId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));
		List<JournalImage> images = journalImageRepository.findByJournalId(journalId);
		journal.softDelete(LocalDateTime.now(seoulClock));
		// daily_journal_rewards는 journal_id에 FK를 두지 않아 삭제 후에도 계정별 당일 보상 판정이 유지된다.

		// 삭제되는 일지의 대표(★) 사진이 식물 대표사진으로 반영돼 있었다면, 대체할 사진이 없으므로 비운다.
		images.stream()
				.filter(JournalImage::isRepresentative)
				.findFirst()
				.ifPresent(representative ->
						plantProfileService.clearThumbnailIfMatches(userId, journal.getPlantProfile(), representative.getImageUrl()));

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
