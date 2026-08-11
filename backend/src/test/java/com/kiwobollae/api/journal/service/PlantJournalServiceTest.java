package com.kiwobollae.api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservation;
import com.kiwobollae.api.commerce.gacha.service.GachaRewardReservationService;
import com.kiwobollae.api.journal.dto.request.JournalImageRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.journal.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.journal.dto.request.PlantJournalUpdateRequest;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.plantProfile.service.PlantProfileService;
import com.kiwobollae.api.point.dto.response.JournalRewardResult;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantJournalServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Mock private PlantJournalRepository plantJournalRepository;
	@Mock private JournalImageRepository journalImageRepository;
	@Mock private PlantProfileRepository plantProfileRepository;
	@Mock private PlantProfileService plantProfileService;
	@Mock private UserRepository userRepository;
	@Mock private WalletService walletService;
	@Mock private GachaRewardReservationService gachaRewardReservationService;
	@Mock private JournalImageUploadService journalImageUploadService;

	@InjectMocks
	private PlantJournalService plantJournalService;

	@Test
	void createJournalRewardsWithSavedJournalId() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournalRequest request = new PlantJournalRequest(
				21L,
				"오늘의 성장 기록",
				List.of(new JournalImageRequest("https://example.test/journal.jpg", "hash", true))
		);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L))
				.willReturn(Optional.of(profile));
		given(journalImageRepository.findExistingHashes(
				eq(7L),
				eq(List.of("hash")),
				any(LocalDate.class)
		)).willReturn(List.of());
		given(userRepository.getReferenceById(7L)).willReturn(user);
		given(plantJournalRepository.save(any(PlantJournal.class))).willAnswer(invocation -> {
			PlantJournal journal = invocation.getArgument(0);
			ReflectionTestUtils.setField(journal, "id", 31L);
			return journal;
		});
		given(plantProfileRepository.claimJournalReward(
				eq(21L),
				any(LocalDateTime.class),
				any(LocalDateTime.class)
		)).willReturn(1);
		given(walletService.rewardJournal(7L, 31L))
				.willReturn(new JournalRewardResult(100L));
		given(gachaRewardReservationService.reserveDailyJournalReward(eq(7L), any(LocalDate.class)))
				.willReturn(GachaRewardReservation.none());

		PlantJournalCreateResponse response = plantJournalService.createJournal(7L, request);

		assertThat(response.journal().id()).isEqualTo(31L);
		assertThat(response.rewardGranted()).isTrue();
		assertThat(response.rewardAmount()).isEqualTo(100L);
		verify(walletService).rewardJournal(7L, 31L);
		verify(gachaRewardReservationService).reserveDailyJournalReward(eq(7L), any(LocalDate.class));
	}

	@Test
	void createJournalDoesNotRewardWhenDailyProfileClaimWasAlreadyUsed() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, LocalDateTime.now(KST));
		PlantJournalRequest request = new PlantJournalRequest(
				21L,
				"같은 날 두 번째 성장 기록",
				List.of(new JournalImageRequest("https://example.test/journal-2.jpg", "hash-2", true))
		);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L))
				.willReturn(Optional.of(profile));
		given(journalImageRepository.findExistingHashes(
				eq(7L),
				eq(List.of("hash-2")),
				any(LocalDate.class)
		)).willReturn(List.of());
		given(userRepository.getReferenceById(7L)).willReturn(user);
		given(plantJournalRepository.save(any(PlantJournal.class))).willAnswer(invocation -> {
			PlantJournal journal = invocation.getArgument(0);
			ReflectionTestUtils.setField(journal, "id", 32L);
			return journal;
		});
		given(plantProfileRepository.claimJournalReward(
				eq(21L),
				any(LocalDateTime.class),
				any(LocalDateTime.class)
		)).willReturn(0);

		PlantJournalCreateResponse response = plantJournalService.createJournal(7L, request);

		assertThat(response.journal().id()).isEqualTo(32L);
		assertThat(response.rewardGranted()).isFalse();
		assertThat(response.rewardAmount()).isZero();
		verifyNoInteractions(walletService);
	}

	@Test
	void createJournalAlwaysUpdatesPlantThumbnailWithRepresentativeImage() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournalRequest request = new PlantJournalRequest(
				21L,
				"대표사진 지정 테스트",
				List.of(new JournalImageRequest("https://example.test/thumb.jpg", "hash-thumb", true))
		);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L))
				.willReturn(Optional.of(profile));
		given(journalImageRepository.findExistingHashes(
				eq(7L),
				eq(List.of("hash-thumb")),
				any(LocalDate.class)
		)).willReturn(List.of());
		given(userRepository.getReferenceById(7L)).willReturn(user);
		given(plantJournalRepository.save(any(PlantJournal.class))).willAnswer(invocation -> {
			PlantJournal journal = invocation.getArgument(0);
			ReflectionTestUtils.setField(journal, "id", 33L);
			return journal;
		});
		given(plantProfileRepository.claimJournalReward(
				eq(21L),
				any(LocalDateTime.class),
				any(LocalDateTime.class)
		)).willReturn(0);

		plantJournalService.createJournal(7L, request);

		verify(plantProfileService).updateThumbnail(7L, profile, "https://example.test/thumb.jpg");
	}

	@Test
	void deleteRewardedJournalKeepsRewardClaimAndDoesNotChangePoints() {
		User user = user(7L);
		LocalDateTime grantedAt = LocalDateTime.now(KST);
		PlantProfile profile = profile(21L, user, grantedAt);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L))
				.willReturn(Optional.of(journal));

		plantJournalService.deleteJournal(7L, 31L);

		assertThat(journal.getDeletedAt()).isNotNull();
		assertThat(profile.getJournalRewardGrantedAt()).isEqualTo(grantedAt);
		verifyNoInteractions(walletService);
	}

	@Test
	void deleteJournalClearsPlantThumbnailWhenRepresentativeImageMatches() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
		given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(
				journalImage("https://example.test/thumb.jpg", true)
		));

		plantJournalService.deleteJournal(7L, 31L);

		verify(plantProfileService).clearThumbnailIfMatches(7L, profile, "https://example.test/thumb.jpg");
	}

	@Test
	void deleteJournalDoesNotTouchThumbnailWhenNoImageIsRepresentative() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
		given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(
				journalImage("https://example.test/a.jpg")
		));

		plantJournalService.deleteJournal(7L, 31L);

		verifyNoInteractions(plantProfileService);
	}

	@Test
	void deleteJournalCleansUpAllImages() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
		given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(
				journalImage("https://example.test/a.jpg"),
				journalImage("https://example.test/b.jpg")
		));

		plantJournalService.deleteJournal(7L, 31L);

		verify(journalImageUploadService).delete("https://example.test/a.jpg", 7L);
		verify(journalImageUploadService).delete("https://example.test/b.jpg", 7L);
	}

	@Test
	void updateJournalDeletesOnlyReplacedImages() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
		given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(
				journalImage("https://example.test/kept.jpg"),
				journalImage("https://example.test/replaced.jpg")
		));
		PlantJournalUpdateRequest request = new PlantJournalUpdateRequest(
				"수정된 기록",
				List.of(
						new JournalImageRequest("https://example.test/kept.jpg", "hash-kept", true),
						new JournalImageRequest("https://example.test/new.jpg", "hash-new", false)
				)
		);
		given(journalImageRepository.findExistingHashes(
				eq(7L), eq(List.of("hash-kept", "hash-new")), any(LocalDate.class)
		)).willReturn(List.of());
		given(userRepository.getReferenceById(7L)).willReturn(user);

		plantJournalService.updateJournal(7L, 31L, request);

		verify(journalImageUploadService).delete("https://example.test/replaced.jpg", 7L);
		verify(journalImageUploadService, never()).delete("https://example.test/kept.jpg", 7L);
		verify(journalImageUploadService, never()).delete("https://example.test/new.jpg", 7L);
	}

	@Test
	void updateJournalUpdatesPlantThumbnailWithNewRepresentativeImage() {
		User user = user(7L);
		PlantProfile profile = profile(21L, user, null);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
		given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(
				journalImage("https://example.test/old.jpg", true)
		));
		PlantJournalUpdateRequest request = new PlantJournalUpdateRequest(
				"수정된 기록",
				List.of(new JournalImageRequest("https://example.test/new-thumb.jpg", "hash-new-thumb", true))
		);
		given(journalImageRepository.findExistingHashes(
				eq(7L), eq(List.of("hash-new-thumb")), any(LocalDate.class)
		)).willReturn(List.of());
		given(userRepository.getReferenceById(7L)).willReturn(user);

		plantJournalService.updateJournal(7L, 31L, request);

		verify(plantProfileService).updateThumbnail(7L, profile, "https://example.test/new-thumb.jpg");
	}

	private JournalImage journalImage(String imageUrl) {
		return journalImage(imageUrl, false);
	}

	private JournalImage journalImage(String imageUrl, boolean representative) {
		return JournalImage.builder()
				.imageUrl(imageUrl)
				.representative(representative)
				.build();
	}

	@Test
	void getProfileIdsWrittenTodayReturnsDistinctProfileIdsForToday() {
		given(plantJournalRepository.findDistinctProfileIdsByUserIdAndWrittenDate(7L, LocalDate.now(KST)))
				.willReturn(List.of(21L, 22L));

		List<Long> result = plantJournalService.getProfileIdsWrittenToday(7L);

		assertThat(result).containsExactly(21L, 22L);
	}

	private User user(Long id) {
		User user = User.builder().build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private PlantProfile profile(Long id, User user, LocalDateTime grantedAt) {
		PlantProfile profile = PlantProfile.builder()
				.user(user)
				.plantName("바질")
				.journalRewardGrantedAt(grantedAt)
				.build();
		ReflectionTestUtils.setField(profile, "id", id);
		return profile;
	}

	private PlantJournal journal(Long id, User user, PlantProfile profile) {
		PlantJournal journal = PlantJournal.builder()
				.user(user)
				.plantProfile(profile)
				.writtenDate(LocalDate.now(KST))
				.createdAt(LocalDateTime.now(KST))
				.updatedAt(LocalDateTime.now(KST))
				.build();
		ReflectionTestUtils.setField(journal, "id", id);
		return journal;
	}
}
