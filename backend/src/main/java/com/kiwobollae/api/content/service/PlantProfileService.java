package com.kiwobollae.api.content.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.dto.request.PlantProfileRequest;
import com.kiwobollae.api.content.dto.request.PlantProfileUpdateRequest;
import com.kiwobollae.api.content.dto.response.PlantProfileResponse;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantProfileService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// 일지 완료 보상 포인트. 임시값 — 추후 팀 협의 후 조정 예정. PlantJournalService와 동일한 값.
	private static final long JOURNAL_REWARD_AMOUNT = 100L;

	private final PlantProfileRepository plantProfileRepository;
	private final PlantSpeciesRepository plantSpeciesRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;

	@Transactional
	public PlantProfileResponse createProfile(Long userId, PlantProfileRequest request) {
		PlantSpecies species = plantSpeciesRepository.findById(request.speciesId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_SPECIES_NOT_FOUND));
		User user = userRepository.getReferenceById(userId);
		PlantProfile saved = plantProfileRepository.save(
				PlantProfile.create(user, species, request.nickname(), request.startDate(), request.thumbnailUrl()));
		return PlantProfileResponse.from(saved);
	}

	public List<PlantProfileResponse> getMyProfiles(Long userId) {
		return plantProfileRepository.findAllByUserId(userId).stream()
				.map(PlantProfileResponse::from)
				.toList();
	}

	public PlantProfileResponse getProfile(Long userId, Long profileId) {
		return PlantProfileResponse.from(findOwned(userId, profileId));
	}

	@Transactional
	public PlantProfileResponse updateProfile(Long userId, Long profileId, PlantProfileUpdateRequest request) {
		PlantProfile profile = findOwned(userId, profileId);
		profile.updateProfile(request.nickname(), request.thumbnailUrl(), request.status());
		return PlantProfileResponse.from(profile);
	}

	@Transactional
	public void deleteProfile(Long userId, Long profileId) {
		PlantProfile profile = findOwned(userId, profileId);

		// 오늘 지급된 보상이면 프로필 삭제 전 회수한다. 매일 리셋 정책이라 어제 이전 지급분은
		// 그대로 둬도 우회 이득이 없다(삭제 여부와 무관하게 오늘 새로 받을 수 있으므로).
		LocalDateTime grantedAt = profile.getJournalRewardGrantedAt();
		boolean grantedToday = grantedAt != null && grantedAt.toLocalDate().equals(LocalDateTime.now(KST).toLocalDate());
		if (grantedToday && plantProfileRepository.clearJournalRewardIfMatches(profileId, grantedAt) == 1) {
			walletService.applyDelta(userId, PointTxType.CLAWBACK, CurrencyType.FREE,
					-JOURNAL_REWARD_AMOUNT, PointRefType.JOURNAL_REVOCATION, profileId);
		}

		journalImageRepository.deleteAllByProfileId(profileId);
		plantJournalRepository.deleteAllByProfileId(profileId);
		plantProfileRepository.delete(profile);
	}

	private PlantProfile findOwned(Long userId, Long profileId) {
		return plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
	}
}
