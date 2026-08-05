package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantTimelapse;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus;
import com.kiwobollae.api.content.dto.response.PlantTimelapseResponse;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.repository.PlantTimelapseRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantTimelapseService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MIN_REPRESENTATIVE_IMAGES = 2;

	private final PlantProfileRepository plantProfileRepository;
	private final PlantTimelapseRepository plantTimelapseRepository;
	private final JournalImageRepository journalImageRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public PlantTimelapseResponse requestTimelapse(Long userId, Long profileId) {
		PlantProfile profile = plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		if (profile.getStatus() == PlantStatus.GROWING) {
			throw new BusinessException(ErrorCode.TIMELAPSE_NOT_HARVESTED);
		}
		int imageCount = journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(profileId).size();
		if (imageCount < MIN_REPRESENTATIVE_IMAGES) {
			throw new BusinessException(ErrorCode.TIMELAPSE_INSUFFICIENT_IMAGES);
		}

		LocalDateTime now = LocalDateTime.now(KST);
		PlantTimelapse timelapse = plantTimelapseRepository.findByPlantProfileId(profileId).orElse(null);
		String previousVideoUrl = null;
		if (timelapse == null) {
			timelapse = plantTimelapseRepository.save(PlantTimelapse.create(profile, now));
		} else if (timelapse.getStatus() == PlantTimelapseStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.TIMELAPSE_ALREADY_PROCESSING);
		} else {
			previousVideoUrl = timelapse.getVideoUrl();
			timelapse.restart(now);
		}

		eventPublisher.publishEvent(new PlantTimelapseRequestedEvent(profileId, previousVideoUrl));
		return PlantTimelapseResponse.from(timelapse);
	}

	public PlantTimelapseResponse getTimelapse(Long userId, Long profileId) {
		plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
		return plantTimelapseRepository.findByPlantProfileId(profileId)
				.map(PlantTimelapseResponse::from)
				.orElseGet(PlantTimelapseResponse::none);
	}
}
