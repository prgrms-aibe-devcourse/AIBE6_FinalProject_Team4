package com.kiwobollae.api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.entity.PlantTimelapse;
import com.kiwobollae.api.content.repository.PlantTimelapseRepository;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantTimelapseTransactionServiceTest {

	@Mock private PlantTimelapseRepository plantTimelapseRepository;
	@Mock private JournalImageRepository journalImageRepository;
	@Mock private JournalImageUploadService journalImageUploadService;
	@Mock private PlantTimelapseVideoStorageService videoStorageService;
	@Mock private FfmpegTimelapseEncoder encoder;
	@Mock private NotificationService notificationService;

	@InjectMocks
	private PlantTimelapseTransactionService transactionService;

	@Test
	void claimReturnsFalseWhenNoRowClaimed() {
		given(plantTimelapseRepository.claimForProcessing(21L)).willReturn(0);

		boolean claimed = transactionService.claim(21L);

		assertThat(claimed).isFalse();
	}

	@Test
	void claimReturnsTrueWhenRowClaimed() {
		given(plantTimelapseRepository.claimForProcessing(21L)).willReturn(1);

		boolean claimed = transactionService.claim(21L);

		assertThat(claimed).isTrue();
	}

	@Test
	void encodeAndUploadEncodesUploadsAndCleansUpPreviousVideo() {
		JournalImage image1 = journalImage(7L, "/api/v1/journals/images/7/a.jpg");
		JournalImage image2 = journalImage(7L, "/api/v1/journals/images/7/b.png");
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(image1, image2));
		given(journalImageUploadService.downloadBytes("/api/v1/journals/images/7/a.jpg")).willReturn("a".getBytes());
		given(journalImageUploadService.downloadBytes("/api/v1/journals/images/7/b.png")).willReturn("b".getBytes());
		given(encoder.encode(any())).willReturn("video-bytes".getBytes());
		given(videoStorageService.uploadVideo(eq(7L), any())).willReturn("/api/v1/plants/timelapse-videos/7/new.mp4");

		String videoUrl = transactionService.encodeAndUpload(21L, "/api/v1/plants/timelapse-videos/7/old.mp4");

		assertThat(videoUrl).isEqualTo("/api/v1/plants/timelapse-videos/7/new.mp4");
		verify(videoStorageService).deleteVideo("/api/v1/plants/timelapse-videos/7/old.mp4");
	}

	@Test
	void encodeAndUploadSkipsCleanupWhenNoPreviousVideo() {
		JournalImage image1 = journalImage(7L, "/api/v1/journals/images/7/a.jpg");
		JournalImage image2 = journalImage(7L, "/api/v1/journals/images/7/b.png");
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of(image1, image2));
		given(journalImageUploadService.downloadBytes("/api/v1/journals/images/7/a.jpg")).willReturn("a".getBytes());
		given(journalImageUploadService.downloadBytes("/api/v1/journals/images/7/b.png")).willReturn("b".getBytes());
		given(encoder.encode(any())).willReturn("video-bytes".getBytes());
		given(videoStorageService.uploadVideo(eq(7L), any())).willReturn("/api/v1/plants/timelapse-videos/7/new.mp4");

		transactionService.encodeAndUpload(21L, null);

		verify(videoStorageService, org.mockito.Mockito.never()).deleteVideo(any());
	}

	// 요청 검증(대표이미지 2장 이상) 시점과 워커 실행(비동기) 시점 사이에 사용자가 일지를 지우면
	// 실제로 빈 리스트가 될 수 있다 — 그 경우 ffmpeg 호출로 흘려보내지 않고 여기서 바로 막는다.
	@Test
	void encodeAndUploadThrowsWhenNoRepresentativeImagesLeft() {
		given(journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(21L))
				.willReturn(List.of());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionService.encodeAndUpload(21L, null))
				.isInstanceOf(TimelapseEncodingException.class);

		verify(encoder, org.mockito.Mockito.never()).encode(any());
		verify(videoStorageService, org.mockito.Mockito.never()).uploadVideo(any(), any());
	}

	@Test
	void completeMarksRowCompletedAndNotifiesOwner() {
		PlantTimelapse existing = PlantTimelapse.create(profile(21L, 7L), java.time.LocalDateTime.now());
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.of(existing));

		transactionService.complete(21L, "/api/v1/plants/timelapse-videos/7/new.mp4");

		assertThat(existing.getStatus()).isEqualTo(com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.COMPLETED);
		assertThat(existing.getVideoUrl()).isEqualTo("/api/v1/plants/timelapse-videos/7/new.mp4");
		verify(notificationService).notify(eq(7L), eq(NotificationType.TIMELAPSE), anyString(), anyString(), anyString(), eq("PLANT_TIMELAPSE"), eq(21L));
	}

	@Test
	void failMarksRowFailedAndNotifiesOwner() {
		PlantTimelapse timelapse = PlantTimelapse.create(profile(21L, 7L), java.time.LocalDateTime.now());
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.of(timelapse));

		transactionService.fail(21L, "ffmpeg exited with code 1");

		assertThat(timelapse.getStatus()).isEqualTo(com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.FAILED);
		assertThat(timelapse.getFailReason()).isEqualTo("ffmpeg exited with code 1");
		verify(notificationService).notify(eq(7L), eq(NotificationType.TIMELAPSE), anyString(), anyString(), anyString(), eq("PLANT_TIMELAPSE"), eq(21L));
	}

	@Test
	void recoverStaleMarksFailedAndNotifiesWhenStillPendingOrProcessing() {
		given(plantTimelapseRepository.failIfStillPendingOrProcessing(eq(21L), anyString(), any())).willReturn(1);
		PlantTimelapse timelapse = PlantTimelapse.create(profile(21L, 7L), java.time.LocalDateTime.now());
		given(plantTimelapseRepository.findByPlantProfileId(21L)).willReturn(Optional.of(timelapse));

		transactionService.recoverStale(21L);

		verify(notificationService).notify(eq(7L), eq(NotificationType.TIMELAPSE), anyString(), anyString(), anyString(), eq("PLANT_TIMELAPSE"), eq(21L));
	}

	@Test
	void recoverStaleDoesNothingWhenAlreadyResolvedByNormalWorker() {
		given(plantTimelapseRepository.failIfStillPendingOrProcessing(eq(21L), anyString(), any())).willReturn(0);

		transactionService.recoverStale(21L);

		verify(plantTimelapseRepository, org.mockito.Mockito.never()).findByPlantProfileId(any());
		verify(notificationService, org.mockito.Mockito.never()).notify(any(), any(), any(), any(), any(), any(), any());
	}

	private JournalImage journalImage(Long userId, String imageUrl) {
		User user = user(userId);
		return JournalImage.builder().user(user).imageUrl(imageUrl).representative(true).build();
	}

	private User user(Long id) {
		User user = User.builder().build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private PlantProfile profile(Long profileId, Long userId) {
		PlantProfile profile = PlantProfile.builder().user(user(userId)).build();
		ReflectionTestUtils.setField(profile, "id", profileId);
		return profile;
	}
}
