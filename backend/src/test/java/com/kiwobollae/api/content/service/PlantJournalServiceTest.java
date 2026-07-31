package com.kiwobollae.api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.dto.request.JournalImageRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalRequest;
import com.kiwobollae.api.content.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
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
	@Mock private UserRepository userRepository;
	@Mock private WalletService walletService;

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

		PlantJournalCreateResponse response = plantJournalService.createJournal(7L, request);

		assertThat(response.journal().id()).isEqualTo(31L);
		assertThat(response.rewardGranted()).isTrue();
		assertThat(response.rewardAmount()).isEqualTo(100L);
		verify(walletService).rewardJournal(7L, 31L);
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
