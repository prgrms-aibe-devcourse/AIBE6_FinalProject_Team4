package com.kiwobollae.api.content.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.content.dto.request.JournalImageRequest;
import com.kiwobollae.api.content.dto.request.PlantJournalRequest;
import com.kiwobollae.api.content.entity.PlantJournal;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.repository.PlantJournalRepository;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
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

		plantJournalService.createJournal(7L, request);

		verify(walletService).rewardJournal(7L, 31L);
	}

	@Test
	void deleteRewardedJournalRevokesWithJournalId() {
		User user = user(7L);
		LocalDateTime grantedAt = LocalDateTime.now(KST);
		PlantProfile profile = profile(21L, user, grantedAt);
		PlantJournal journal = journal(31L, user, profile);
		given(plantJournalRepository.findOwnedActive(31L, 7L))
				.willReturn(Optional.of(journal));
		given(walletService.hasActiveJournalReward(7L, 31L)).willReturn(true);
		given(plantProfileRepository.clearJournalRewardIfMatches(21L, grantedAt))
				.willReturn(1);

		plantJournalService.deleteJournal(7L, 31L);

		verify(walletService).revokeJournalReward(7L, 31L);
	}

	@Test
	void deleteJournalWithoutRewardLedgerDoesNotRequestRevocation() {
		User user = user(7L);
		LocalDateTime grantedAt = LocalDateTime.now(KST);
		PlantProfile profile = profile(21L, user, grantedAt);
		PlantJournal journal = journal(32L, user, profile);
		given(plantJournalRepository.findOwnedActive(32L, 7L))
				.willReturn(Optional.of(journal));
		given(walletService.hasActiveJournalReward(7L, 32L)).willReturn(false);

		plantJournalService.deleteJournal(7L, 32L);

		verify(plantProfileRepository, never())
				.clearJournalRewardIfMatches(any(Long.class), any(LocalDateTime.class));
		verify(walletService, never()).revokeJournalReward(any(Long.class), any(Long.class));
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
