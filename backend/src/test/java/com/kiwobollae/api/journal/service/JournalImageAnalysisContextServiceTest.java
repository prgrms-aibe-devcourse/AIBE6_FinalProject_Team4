package com.kiwobollae.api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.dto.response.JournalImageAnalysisContext;
import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.species.entity.PlantSpecies;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class JournalImageAnalysisContextServiceTest {

  @Mock private PlantJournalRepository plantJournalRepository;
  @Mock private JournalImageRepository journalImageRepository;
  @Mock private PlantJournal journal;
  @Mock private PlantJournal recentJournal;
  @Mock private JournalImage image;
  @Mock private PlantProfile profile;
  @Mock private PlantSpecies species;

  @Test
  void validatesOwnedCurrentImageWithoutLoadingFullJournalContext() {
    given(journalImageRepository.existsOwnedActiveImage(7L, 31L, "a".repeat(64)))
        .willReturn(true);

    service().validateAnalysisTarget(7L, 31L, "a".repeat(64));

    verify(journalImageRepository).existsOwnedActiveImage(7L, 31L, "a".repeat(64));
    verifyNoInteractions(plantJournalRepository);
  }

  @Test
  void rejectsImageThatIsNotAttachedToOwnedActiveJournal() {
    assertThatThrownBy(() -> service().validateAnalysisTarget(7L, 31L, "a".repeat(64)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_IMAGE_ANALYSIS_IMAGE_NOT_FOUND));

    verify(journalImageRepository).existsOwnedActiveImage(7L, 31L, "a".repeat(64));
    verifyNoInteractions(plantJournalRepository);
  }

  @Test
  void exposesOwnedSavedJournalThroughReadOnlyAiContract() {
    given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.of(journal));
    given(journalImageRepository.findByJournalId(31L)).willReturn(List.of(image));
    given(journal.getId()).willReturn(31L);
    given(journal.getPlantProfile()).willReturn(profile);
    given(journal.getWrittenDate()).willReturn(LocalDate.of(2026, 8, 13));
    given(journal.getContent()).willReturn("아래쪽 잎을 관찰했어요.");
    given(profile.getId()).willReturn(21L);
    given(profile.getPlantName()).willReturn("바질이");
    given(profile.getSpecies()).willReturn(species);
    given(species.getName()).willReturn("스위트 바질");
    given(species.getCategory()).willReturn("허브");
    given(species.getCareGuide()).willReturn("겉흙이 마르면 물을 주세요.");
    given(image.getImageUrl()).willReturn("/api/v1/journals/images/7/basil.webp");
    given(image.getImageHash()).willReturn("a".repeat(64));
    given(image.isRepresentative()).willReturn(true);
    given(
            plantJournalRepository.findRecentActiveByProfileExcluding(
                7L, 21L, 31L, PageRequest.of(0, 5)))
        .willReturn(List.of(recentJournal));
    given(recentJournal.getWrittenDate()).willReturn(LocalDate.of(2026, 8, 12));
    given(recentJournal.getContent()).willReturn("새 잎이 두 장 자랐어요.");

    JournalImageAnalysisContext context = service().getAnalysisContext(7L, 31L, 5);

    assertThat(context.plantNickname()).isEqualTo("바질이");
    assertThat(context.speciesName()).isEqualTo("스위트 바질");
    assertThat(context.images())
        .singleElement()
        .satisfies(
            savedImage -> {
              assertThat(savedImage.imageHash()).isEqualTo("a".repeat(64));
              assertThat(savedImage.representative()).isTrue();
            });
    assertThat(context.recentJournals())
        .extracting(JournalImageAnalysisContext.RecentJournal::content)
        .containsExactly("새 잎이 두 장 자랐어요.");
    verify(plantJournalRepository)
        .findRecentActiveByProfileExcluding(7L, 21L, 31L, PageRequest.of(0, 5));
  }

  @Test
  void hidesJournalThatDoesNotBelongToAuthenticatedUser() {
    given(plantJournalRepository.findOwnedActive(31L, 7L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getAnalysisContext(7L, 31L, 5))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOURNAL_NOT_FOUND));

    verifyNoInteractions(journalImageRepository);
  }

  @Test
  void rejectsExcessiveRecentJournalLimit() {
    assertThatThrownBy(() -> service().getAnalysisContext(7L, 31L, 11))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
    verify(plantJournalRepository, org.mockito.Mockito.never()).findOwnedActive(any(), any());
  }

  private JournalImageAnalysisContextService service() {
    return new JournalImageAnalysisContextService(
        plantJournalRepository, journalImageRepository);
  }
}
