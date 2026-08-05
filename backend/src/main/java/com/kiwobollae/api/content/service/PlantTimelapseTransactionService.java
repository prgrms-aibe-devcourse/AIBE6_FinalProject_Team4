package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.entity.JournalImage;
import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.content.entity.PlantTimelapse;
import com.kiwobollae.api.content.repository.PlantTimelapseRepository;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlantTimelapseTransactionService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PlantTimelapseRepository plantTimelapseRepository;
	private final JournalImageRepository journalImageRepository;
	private final JournalImageUploadService journalImageUploadService;
	private final PlantTimelapseVideoStorageService videoStorageService;
	private final FfmpegTimelapseEncoder encoder;
	private final NotificationService notificationService;

	// FFmpeg 실행과 S3 I/O는 수초 걸릴 수 있는 외부 프로세스 호출이라, DB 트랜잭션 밖(claim/complete
	// 사이)에서 수행한다 — 커넥션을 그 시간만큼 붙잡아두지 않기 위함.
	public void process(Long profileId) {
		if (!claim(profileId)) {
			return;
		}

		List<JournalImage> images = journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(profileId);
		List<TimelapseSourceImage> sources = images.stream()
				.map(image -> new TimelapseSourceImage(
						journalImageUploadService.downloadBytes(image.getImageUrl()),
						extensionOf(image.getImageUrl())))
				.toList();
		byte[] videoBytes = encoder.encode(sources);

		Long ownerId = images.get(0).getUser().getId();
		String videoUrl = videoStorageService.uploadVideo(ownerId, videoBytes);

		String previousVideoUrl = plantTimelapseRepository.findByPlantProfileId(profileId)
				.map(PlantTimelapse::getVideoUrl)
				.orElse(null);
		if (previousVideoUrl != null) {
			videoStorageService.deleteVideo(previousVideoUrl);
		}

		complete(profileId, videoUrl);
	}

	@Transactional
	public boolean claim(Long profileId) {
		return plantTimelapseRepository.claimForProcessing(profileId) > 0;
	}

	@Transactional
	public void complete(Long profileId, String videoUrl) {
		PlantTimelapse timelapse = plantTimelapseRepository.findByPlantProfileId(profileId).orElseThrow();
		timelapse.complete(videoUrl, LocalDateTime.now(KST));
		notify(timelapse, "타임랩스가 완성됐어요 🌱", "식물 성장 타임랩스 영상이 만들어졌어요. 지금 확인해보세요!");
	}

	@Transactional
	public void fail(Long profileId, String reason) {
		PlantTimelapse timelapse = plantTimelapseRepository.findByPlantProfileId(profileId).orElseThrow();
		timelapse.fail(reason, LocalDateTime.now(KST));
		notify(timelapse, "타임랩스 생성에 실패했어요", "타임랩스 영상 생성 중 문제가 발생했어요. 다시 시도해 주세요.");
	}

	private void notify(PlantTimelapse timelapse, String title, String content) {
		Long ownerId = timelapse.getPlantProfile().getUser().getId();
		notificationService.notify(ownerId, NotificationType.TIMELAPSE, title, content,
				"/plants/" + timelapse.getPlantProfile().getId(), "PLANT_TIMELAPSE", timelapse.getPlantProfile().getId());
	}

	private String extensionOf(String imageUrl) {
		int dot = imageUrl.lastIndexOf('.');
		return dot >= 0 ? imageUrl.substring(dot) : ".jpg";
	}
}
