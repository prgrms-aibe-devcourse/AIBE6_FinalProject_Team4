package com.kiwobollae.api.timelapse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.timelapse.entity.PlantTimelapse;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.timelapse.entity.enums.PlantTimelapseStatus;
import com.kiwobollae.api.timelapse.dto.response.PlantTimelapseResponse;
import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.timelapse.repository.PlantTimelapseRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantTimelapseServiceTest {

	@Mock private PlantProfileRepository plantProfileRepository;
	@Mock private PlantTimelapseRepository plantTimelapseRepository;
	@Mock private JournalImageRepository journalImageRepository;
	@Mock private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private PlantTimelapseService plantTimelapseService;

	@Test
	void requestTimelapseThrowsWhenProfileNotOwned() {
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> plantTimelapseService.requestTimelapse(7L, 21L))
				.isInstanceOf(BusinessException.class);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void requestTimelapseThrowsWhenStillGrowing() {
		PlantProfile profile = profile(21L, PlantStatus.GROWING);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));

		assertThatThrownBy(() -> plantTimelapseService.requestTimelapse(7L, 21L))
				.isInstanceOf(BusinessException.class);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void requestTimelapseThrowsWhenFewerThanTwoRepresentativeImages() {
		PlantProfile profile = profile(21L, PlantStatus.HARVESTED);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(journalImage()));

		assertThatThrownBy(() -> plantTimelapseService.requestTimelapse(7L, 21L))
				.isInstanceOf(BusinessException.class);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void requestTimelapseThrowsWhenAlreadyProcessing() {
		PlantProfile profile = profile(21L, PlantStatus.HARVESTED);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(journalImage(), journalImage()));
		PlantTimelapse processing = PlantTimelapse.create(profile, java.time.LocalDateTime.now());
		ReflectionTestUtils.setField(processing, "status", PlantTimelapseStatus.PROCESSING);
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.of(processing));

		assertThatThrownBy(() -> plantTimelapseService.requestTimelapse(7L, 21L))
				.isInstanceOf(BusinessException.class);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void requestTimelapseCreatesPendingAndPublishesEventWhenNoExistingRecord() {
		PlantProfile profile = profile(21L, PlantStatus.HARVESTED);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(journalImage(), journalImage()));
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.empty());
		given(plantTimelapseRepository.save(any(PlantTimelapse.class))).willAnswer(invocation -> invocation.getArgument(0));

		PlantTimelapseResponse response = plantTimelapseService.requestTimelapse(7L, 21L);

		assertThat(response.status()).isEqualTo("PENDING");
		verify(eventPublisher).publishEvent(new PlantTimelapseRequestedEvent(21L, null));
	}

	@Test
	void requestTimelapseRestartsExistingCompletedRecord() {
		PlantProfile profile = profile(21L, PlantStatus.HARVESTED);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(journalImage(), journalImage()));
		PlantTimelapse completed = PlantTimelapse.create(profile, java.time.LocalDateTime.now());
		completed.complete("/api/v1/plants/timelapse-videos/7/old.mp4", java.time.LocalDateTime.now());
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.of(completed));

		PlantTimelapseResponse response = plantTimelapseService.requestTimelapse(7L, 21L);

		assertThat(response.status()).isEqualTo("PENDING");
		verify(plantTimelapseRepository, never()).save(any());
		verify(eventPublisher).publishEvent(new PlantTimelapseRequestedEvent(21L, "/api/v1/plants/timelapse-videos/7/old.mp4"));
	}

	@Test
	void getTimelapseReturnsNoneWhenNoRecordExists() {
		PlantProfile profile = profile(21L, PlantStatus.HARVESTED);
		given(plantProfileRepository.findByIdAndUserId(21L, 7L)).willReturn(Optional.of(profile));
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.empty());

		PlantTimelapseResponse response = plantTimelapseService.getTimelapse(7L, 21L);

		assertThat(response.status()).isEqualTo("NONE");
	}

	private PlantProfile profile(Long id, PlantStatus status) {
		PlantProfile profile = PlantProfile.builder().status(status).build();
		ReflectionTestUtils.setField(profile, "id", id);
		return profile;
	}

	private JournalImage journalImage() {
		return JournalImage.builder().representative(true).build();
	}
}
