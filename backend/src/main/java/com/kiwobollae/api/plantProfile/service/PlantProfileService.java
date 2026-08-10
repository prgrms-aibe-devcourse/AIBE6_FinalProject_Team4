package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.plantProfile.dto.request.PlantProfileRequest;
import com.kiwobollae.api.plantProfile.dto.request.PlantProfileUpdateRequest;
import com.kiwobollae.api.plantProfile.dto.response.PlantProfileResponse;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.service.PlantImageUploadService;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import com.kiwobollae.api.timelapse.repository.PlantTimelapseRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;

import com.kiwobollae.api.timelapse.service.PlantTimelapseVideoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantProfileService {

	// 프론트엔드가 "사진 대신 이모지"를 thumbnailUrl에 인코딩할 때 쓰는 접두사(frontend/lib/plant-visual.ts의
	// EMOJI_THUMBNAIL_PREFIX와 값이 같아야 한다) — 실제 S3 업로드 URL이 아니므로 정리 대상에서 제외한다.
	private static final String EMOJI_THUMBNAIL_PREFIX = "emoji:";

	private final PlantProfileRepository plantProfileRepository;
	private final PlantSpeciesRepository plantSpeciesRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;
	private final PlantTimelapseRepository plantTimelapseRepository;
	private final PlantImageUploadService plantImageUploadService;
	private final JournalImageUploadService journalImageUploadService;
	private final PlantTimelapseVideoStorageService plantTimelapseVideoStorageService;
	private final UserRepository userRepository;

	@Transactional
	public PlantProfileResponse createProfile(Long userId, PlantProfileRequest request) {
		PlantSpecies species = plantSpeciesRepository.findById(request.speciesId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_SPECIES_NOT_FOUND));
		User user = userRepository.getReferenceById(userId);
		PlantProfile saved = plantProfileRepository.save(
				PlantProfile.create(user, species, request.nickname(), request.startDate(), request.thumbnailUrl()));
		return PlantProfileResponse.from(saved);
	}

	public Page<PlantProfileResponse> getMyProfiles(Long userId, PlantStatus status, Pageable pageable) {
		return plantProfileRepository.findAllByUserIdAndStatus(userId, status, pageable)
				.map(PlantProfileResponse::from);
	}

	public PlantProfileResponse getProfile(Long userId, Long profileId) {
		return PlantProfileResponse.from(findOwned(userId, profileId));
	}

	@Transactional
	public PlantProfileResponse updateProfile(Long userId, Long profileId, PlantProfileUpdateRequest request) {
		PlantProfile profile = findOwned(userId, profileId);
		String previousThumbnailUrl = profile.getPlantImage();
		profile.updateProfile(request.nickname(), request.thumbnailUrl(), request.status());

		// updateProfile()은 thumbnailUrl이 null이면 기존 값을 유지하므로, 실제로 교체된 경우에만 이전 이미지를 정리한다.
		if (request.thumbnailUrl() != null && !request.thumbnailUrl().equals(previousThumbnailUrl)) {
			deleteThumbnailIfUploaded(previousThumbnailUrl, userId);
		}
		return PlantProfileResponse.from(profile);
	}

	@Transactional
	public void deleteProfile(Long userId, Long profileId) {
		PlantProfile profile = findOwned(userId, profileId);
		List<JournalImage> journalImages = journalImageRepository.findByProfileId(profileId);
		// 삭제 순서와 무관하게 안전하도록, DB 삭제 전에 정리에 필요한 값을 미리 뽑아둔다.
		String thumbnailUrl = profile.getPlantImage();
		String timelapseVideoUrl = plantTimelapseRepository.findVideoUrlByPlantProfileId(profileId).orElse(null);

		journalImageRepository.deleteAllByProfileId(profileId);
		plantJournalRepository.deleteAllByProfileId(profileId);
		plantTimelapseRepository.deleteByPlantProfileId(profileId);
		plantProfileRepository.delete(profile);

		// DB 삭제가 끝난 뒤 S3 정리 — 정리 실패가 프로필 삭제 자체를 막지 않도록 delete()는 항상 예외 없이 반환한다.
		// 일지 이미지는 journals/ 경로로 업로드되므로 JournalImageUploadService로 지워야 한다
		// (plants/ 경로만 인식하는 PlantImageUploadService로는 조용히 무시된다).
		journalImages.forEach(image -> journalImageUploadService.delete(image.getImageUrl(), userId));
		deleteThumbnailIfUploaded(thumbnailUrl, userId);
		if (timelapseVideoUrl != null) {
			plantTimelapseVideoStorageService.deleteVideo(timelapseVideoUrl);
		}
	}

	private void deleteThumbnailIfUploaded(String thumbnailUrl, Long userId) {
		if (thumbnailUrl != null && !thumbnailUrl.startsWith(EMOJI_THUMBNAIL_PREFIX)) {
			plantImageUploadService.delete(thumbnailUrl, userId);
		}
	}

	private PlantProfile findOwned(Long userId, Long profileId) {
		return plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
	}
}
