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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantProfileService {

	private final PlantProfileRepository plantProfileRepository;
	private final PlantSpeciesRepository plantSpeciesRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final JournalImageRepository journalImageRepository;
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

		journalImageRepository.deleteAllByProfileId(profileId);
		plantJournalRepository.deleteAllByProfileId(profileId);
		plantProfileRepository.delete(profile);
	}

	private PlantProfile findOwned(Long userId, Long profileId) {
		return plantProfileRepository.findByIdAndUserId(profileId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));
	}
}
