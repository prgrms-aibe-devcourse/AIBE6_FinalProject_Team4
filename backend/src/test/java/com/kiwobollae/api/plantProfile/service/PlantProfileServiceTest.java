package com.kiwobollae.api.plantProfile.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.plantProfile.dto.request.PlantProfileUpdateRequest;
import com.kiwobollae.api.plantProfile.dto.response.PlantProfileResponse;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.service.JournalImageUploadService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

	// ---- updateThumbnail ----
	// updateThumbnail()은 S3 삭제를 트랜잭션 afterCommit까지 미룬다 — 여기서는 실제 Spring 트랜잭션
	// 없이 TransactionSynchronizationManager를 직접 열고/닫아 커밋·롤백 각각을 시뮬레이션한다.

	@Test
	void updateThumbnailReplacesImageAndCleansUpPreviousUploadAfterCommit() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/old.jpg");

		runInCommittedTransaction(() ->
				plantProfileService.updateThumbnail(7L, profile, "https://cdn.test/journals/7/new.jpg"));

		assertThat(profile.getPlantImage()).isEqualTo("https://cdn.test/journals/7/new.jpg");
		verify(plantImageUploadService).delete("https://cdn.test/plants/7/old.jpg", 7L);
	}

	@Test
	void updateThumbnailSkipsCleanupWhenPreviousWasEmoji() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "emoji:🌱");

		runInCommittedTransaction(() ->
				plantProfileService.updateThumbnail(7L, profile, "https://cdn.test/journals/7/new.jpg"));

		assertThat(profile.getPlantImage()).isEqualTo("https://cdn.test/journals/7/new.jpg");
		verifyNoInteractions(plantImageUploadService);
	}

	@Test
	void updateThumbnailDoesNothingWhenUrlUnchanged() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/same.jpg");

		plantProfileService.updateThumbnail(7L, profile, "https://cdn.test/plants/7/same.jpg");

		verifyNoInteractions(plantImageUploadService);
	}

	@Test
	void updateThumbnailSkipsS3CleanupWhenTransactionRollsBack() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/old.jpg");

		// afterCommit을 트리거하지 않고 그대로 닫는다 — 트랜잭션 롤백 시 afterCommit이 호출되지 않는 것과 동일하다.
		runInRolledBackTransaction(() ->
				plantProfileService.updateThumbnail(7L, profile, "https://cdn.test/journals/7/new.jpg"));

		verifyNoInteractions(plantImageUploadService);
	}

	// ---- clearThumbnailIfMatches ----

	@Test
	void clearThumbnailIfMatchesClearsAndCleansUpWhenUrlMatches() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/journals/7/thumb.jpg");

		runInCommittedTransaction(() ->
				plantProfileService.clearThumbnailIfMatches(7L, profile, "https://cdn.test/journals/7/thumb.jpg"));

		assertThat(profile.getPlantImage()).isNull();
		verify(plantImageUploadService).delete("https://cdn.test/journals/7/thumb.jpg", 7L);
	}

	@Test
	void clearThumbnailIfMatchesDoesNothingWhenUrlAlreadyReplaced() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/plants/7/other.jpg");

		plantProfileService.clearThumbnailIfMatches(7L, profile, "https://cdn.test/journals/7/thumb.jpg");

		assertThat(profile.getPlantImage()).isEqualTo("https://cdn.test/plants/7/other.jpg");
		verifyNoInteractions(plantImageUploadService);
	}

	@Test
	void clearThumbnailIfMatchesSkipsS3CleanupWhenTransactionRollsBack() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, "https://cdn.test/journals/7/thumb.jpg");

		runInRolledBackTransaction(() ->
				plantProfileService.clearThumbnailIfMatches(7L, profile, "https://cdn.test/journals/7/thumb.jpg"));

		verifyNoInteractions(plantImageUploadService);
	}

	private void runInCommittedTransaction(Runnable action) {
		TransactionSynchronizationManager.initSynchronization();
		try {
			action.run();
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private void runInRolledBackTransaction(Runnable action) {
		TransactionSynchronizationManager.initSynchronization();
		try {
			action.run();
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
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
