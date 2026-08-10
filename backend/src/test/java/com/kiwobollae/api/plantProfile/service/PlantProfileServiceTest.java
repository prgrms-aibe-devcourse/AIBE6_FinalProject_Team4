package com.kiwobollae.api.plantProfile.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.plantProfile.dto.requset.PlantProfileUpdateRequest;
import com.kiwobollae.api.plantProfile.dto.response.PlantProfileResponse;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.service.PlantImageUploadService;
import com.kiwobollae.api.plantProfile.service.JournalImageUploadService;
import com.kiwobollae.api.plantProfile.service.PlantProfileService;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import com.kiwobollae.api.timelapse.repository.PlantTimelapseRepository;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.kiwobollae.api.timelapse.service.PlantTimelapseVideoStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantProfileServiceTest {

	@Mock private PlantProfileRepository plantProfileRepository;
	@Mock private PlantSpeciesRepository plantSpeciesRepository;
	@Mock private PlantJournalRepository plantJournalRepository;
	@Mock private JournalImageRepository journalImageRepository;
	@Mock private PlantTimelapseRepository plantTimelapseRepository;
	@Mock private PlantImageUploadService plantImageUploadService;
	@Mock private JournalImageUploadService journalImageUploadService;
	@Mock private PlantTimelapseVideoStorageService plantTimelapseVideoStorageService;
	@Mock private com.kiwobollae.api.auth.repository.UserRepository userRepository;

	@InjectMocks
	private PlantProfileService plantProfileService;

	@Test
	void deleteProfileCleansUpJournalImagesAndUploadedThumbnail() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/thumb.jpg");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findByProfileId(21L)).willReturn(List.of(
				journalImage("https://cdn.test/journals/7/a.jpg"),
				journalImage("https://cdn.test/journals/7/b.jpg")
		));

		plantProfileService.deleteProfile(7L, 21L);

		// 일지 이미지는 journals/ 경로라 JournalImageUploadService로 지워야 한다.
		verify(journalImageUploadService).delete("https://cdn.test/journals/7/a.jpg", 7L);
		verify(journalImageUploadService).delete("https://cdn.test/journals/7/b.jpg", 7L);
		// 프로필 자체의 대표 사진은 plants/ 경로라 PlantImageUploadService로 지운다.
		verify(plantImageUploadService).delete("https://cdn.test/plants/7/thumb.jpg", 7L);
		verify(plantImageUploadService, never()).delete("https://cdn.test/journals/7/a.jpg", 7L);
		verify(plantImageUploadService, never()).delete("https://cdn.test/journals/7/b.jpg", 7L);
	}

	@Test
	void deleteProfileSkipsEmojiThumbnailCleanup() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "emoji:🌱");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findByProfileId(21L)).willReturn(List.of());

		plantProfileService.deleteProfile(7L, 21L);

		verifyNoInteractions(plantImageUploadService);
		verifyNoInteractions(journalImageUploadService);
	}

	@Test
	void deleteProfileCleansUpCompletedTimelapseVideo() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "emoji:🌱");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findByProfileId(21L)).willReturn(List.of());
		given(plantTimelapseRepository.findVideoUrlByPlantProfileId(21L))
				.willReturn(Optional.of("/api/v1/plants/timelapse-videos/7/abc.mp4"));

		plantProfileService.deleteProfile(7L, 21L);

		verify(plantTimelapseVideoStorageService).deleteVideo("/api/v1/plants/timelapse-videos/7/abc.mp4");
	}

	@Test
	void deleteProfileSkipsVideoCleanupWhenNoCompletedTimelapseExists() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "emoji:🌱");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findByProfileId(21L)).willReturn(List.of());
		given(plantTimelapseRepository.findVideoUrlByPlantProfileId(21L)).willReturn(Optional.empty());

		plantProfileService.deleteProfile(7L, 21L);

		verifyNoInteractions(plantTimelapseVideoStorageService);
	}

	@Test
	void updateProfileDeletesPreviousThumbnailWhenReplaced() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/old.jpg");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		PlantProfileUpdateRequest request = new PlantProfileUpdateRequest(null, "https://cdn.test/plants/7/new.jpg", null);

		plantProfileService.updateProfile(7L, 21L, request);

		verify(plantImageUploadService).delete("https://cdn.test/plants/7/old.jpg", 7L);
	}

	@Test
	void updateProfileKeepsThumbnailWhenRequestUrlIsNull() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/old.jpg");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		PlantProfileUpdateRequest request = new PlantProfileUpdateRequest("새 별명", null, null);

		plantProfileService.updateProfile(7L, 21L, request);

		verifyNoInteractions(plantImageUploadService);
	}

	@Test
	void updateProfileDoesNotCleanUpWhenThumbnailUnchanged() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/same.jpg");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		PlantProfileUpdateRequest request = new PlantProfileUpdateRequest(null, "https://cdn.test/plants/7/same.jpg", null);

		plantProfileService.updateProfile(7L, 21L, request);

		verifyNoInteractions(plantImageUploadService);
	}

	@Test
	void updateProfileDoesNotCleanUpPreviousEmojiThumbnail() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "emoji:🍅");
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		PlantProfileUpdateRequest request = new PlantProfileUpdateRequest(null, "https://cdn.test/plants/7/new.jpg", null);

		plantProfileService.updateProfile(7L, 21L, request);

		verify(plantImageUploadService, never()).delete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
	}

	// ---- getMyProfiles ----

	@Test
	void getMyProfilesMapsRepositoryPageWhenStatusIsNull() {
		Pageable pageable = PageRequest.of(0, 20);
		PlantProfile profile = profile(21L, user(7L), "emoji:🌱");
		given(plantProfileRepository.findAllByUserIdAndStatus(7L, null, pageable))
				.willReturn(new PageImpl<>(List.of(profile)));

		Page<PlantProfileResponse> result = plantProfileService.getMyProfiles(7L, null, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).id()).isEqualTo(21L);
	}

	@Test
	void getMyProfilesFiltersByStatus() {
		Pageable pageable = PageRequest.of(0, 20);
		given(plantProfileRepository.findAllByUserIdAndStatus(7L, PlantStatus.HARVESTED, pageable))
				.willReturn(new PageImpl<>(List.of()));

		Page<PlantProfileResponse> result = plantProfileService.getMyProfiles(7L, PlantStatus.HARVESTED, pageable);

		assertThat(result.getContent()).isEmpty();
		verify(plantProfileRepository).findAllByUserIdAndStatus(7L, PlantStatus.HARVESTED, pageable);
	}

	private User user(Long id) {
		User user = User.builder().build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private PlantProfile profile(Long id, User user, String plantImage) {
		PlantProfile profile = PlantProfile.builder()
				.user(user)
				.species(PlantSpecies.builder().name("바질").build())
				.plantName("바질이")
				.startDate(LocalDate.now())
				.plantImage(plantImage)
				.status(PlantStatus.GROWING)
				.build();
		ReflectionTestUtils.setField(profile, "id", id);
		return profile;
	}

	private JournalImage journalImage(String imageUrl) {
		return JournalImage.builder()
				.imageUrl(imageUrl)
				.build();
	}
}
