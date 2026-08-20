package com.kiwobollae.api.plantProfile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantGrowthContextServiceTest {

	@Mock private PlantProfileRepository plantProfileRepository;
	@Mock private PlantJournalRepository plantJournalRepository;

	@InjectMocks private PlantGrowthContextService growthContextService;

	@Test
	void returnsOwnedProfileAndRecentJournalsThroughPlantProfileBoundary() {
		PlantProfile profile = profile();
		List<PlantJournal> journals = List.of(
				journal(32L, LocalDate.of(2026, 8, 9), "최신 기록"),
				journal(31L, LocalDate.of(2026, 8, 7), "이전 기록")
		);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(plantJournalRepository.findRecentActiveByProfile(eq(7L), eq(21L), org.mockito.ArgumentMatchers.any()))
				.willReturn(journals);

		PlantGrowthContextResponse context = growthContextService.getJournalHistoryContext(7L, 21L);

		assertThat(context.profileId()).isEqualTo(21L);
		assertThat(context.nickname()).isEqualTo("바질이");
		assertThat(context.speciesName()).isEqualTo("바질");
		assertThat(context.recentJournals())
				.extracting(PlantGrowthContextResponse.RecentJournal::journalId)
				.containsExactly(32L, 31L);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(plantJournalRepository).findRecentActiveByProfile(eq(7L), eq(21L), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
	}

	@Test
	void returnsJournalHistoryCandidatesBeyondFiveRecentJournals() {
		PlantProfile profile = profile();
		List<PlantJournal> journals = List.of(
				journal(36L, LocalDate.of(2026, 8, 13), "12345"),
				journal(35L, LocalDate.of(2026, 8, 12), "67890"),
				journal(34L, LocalDate.of(2026, 8, 11), "abcde"),
				journal(33L, LocalDate.of(2026, 8, 10), "fghij"),
				journal(32L, LocalDate.of(2026, 8, 9), "klmno"),
				journal(31L, LocalDate.of(2026, 8, 8), "pqrst"),
				journal(30L, LocalDate.of(2026, 8, 7), "uvwxyz")
		);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(plantJournalRepository.findRecentActiveByProfile(eq(7L), eq(21L), org.mockito.ArgumentMatchers.any()))
				.willReturn(journals);

		PlantGrowthContextResponse context = growthContextService.getJournalHistoryContext(7L, 21L);

		assertThat(context.recentJournals())
				.extracting(PlantGrowthContextResponse.RecentJournal::journalId)
				.containsExactly(36L, 35L, 34L, 33L, 32L, 31L, 30L);
	}

	@Test
	void hidesWhetherAnotherUsersProfileExists() {
		given(plantProfileRepository.findByIdAndUserId(99L, 7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> growthContextService.getJournalHistoryContext(7L, 99L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLANT_PROFILE_NOT_FOUND));

		verifyNoInteractions(plantJournalRepository);
	}

	@Test
	void verifiesOwnershipWithoutReadingJournalHistory() {
		given(plantProfileRepository.existsByIdAndUserId(21L, 7L)).willReturn(true);

		growthContextService.verifyOwnership(7L, 21L);

		verify(plantProfileRepository).existsByIdAndUserId(21L, 7L);
		verifyNoInteractions(plantJournalRepository);
	}

	@Test
	void rejectsUnownedProfileBeforeReadingJournalHistory() {
		given(plantProfileRepository.existsByIdAndUserId(99L, 7L)).willReturn(false);

		assertThatThrownBy(() -> growthContextService.verifyOwnership(7L, 99L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLANT_PROFILE_NOT_FOUND));

		verifyNoInteractions(plantJournalRepository);
	}

	@Test
	void limitsRecentGrowthContextToTenJournals() {
		PlantProfile profile = profile();
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(plantJournalRepository.findRecentActiveByProfile(eq(7L), eq(21L), org.mockito.ArgumentMatchers.any()))
				.willReturn(List.of());

		growthContextService.getRecentGrowthContext(7L, 21L);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(plantJournalRepository).findRecentActiveByProfile(eq(7L), eq(21L), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
	}

	private PlantProfile profile() {
		PlantProfile profile = PlantProfile.builder()
				.speciesName("바질")
				.plantName("바질이")
				.startDate(LocalDate.of(2026, 7, 1))
				.status(PlantStatus.GROWING)
				.build();
		ReflectionTestUtils.setField(profile, "id", 21L);
		return profile;
	}

	private PlantJournal journal(Long id, LocalDate writtenDate, String content) {
		PlantJournal journal = PlantJournal.builder()
				.content(content)
				.writtenDate(writtenDate)
				.build();
		ReflectionTestUtils.setField(journal, "id", id);
		return journal;
	}
}
