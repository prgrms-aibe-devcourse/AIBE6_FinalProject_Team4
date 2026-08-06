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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlantTimelapseTransactionService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PlantTimelapseRepository plantTimelapseRepository;
	private final JournalImageRepository journalImageRepository;
	private final JournalImageUploadService journalImageUploadService;
	private final PlantTimelapseVideoStorageService videoStorageService;
	private final FfmpegTimelapseEncoder encoder;
	private final NotificationService notificationService;
	private final Executor downloadExecutor;

	// Lombok의 @RequiredArgsConstructor가 필드의 @Qualifier를 생성자 파라미터로 복사해주지
	// 않아(실제로 확인됨 — 애플리케이션 전체에 Executor 빈이 여러 개라 모호해짐), 여기만
	// 명시적으로 생성자를 쓴다. downloadExecutor는 반드시 timelapseDownloadExecutor여야
	// 한다 — 다른 Executor 빈(gachaTaskExecutor, timelapseTaskExecutor 등)과 섞이면 안 된다.
	public PlantTimelapseTransactionService(
			PlantTimelapseRepository plantTimelapseRepository,
			JournalImageRepository journalImageRepository,
			JournalImageUploadService journalImageUploadService,
			PlantTimelapseVideoStorageService videoStorageService,
			FfmpegTimelapseEncoder encoder,
			NotificationService notificationService,
			@Qualifier("timelapseDownloadExecutor") Executor downloadExecutor) {
		this.plantTimelapseRepository = plantTimelapseRepository;
		this.journalImageRepository = journalImageRepository;
		this.journalImageUploadService = journalImageUploadService;
		this.videoStorageService = videoStorageService;
		this.encoder = encoder;
		this.notificationService = notificationService;
		this.downloadExecutor = downloadExecutor;
	}

	@Transactional
	public boolean claim(Long profileId) {
		return plantTimelapseRepository.claimForProcessing(profileId) > 0;
	}

	// FFmpeg 실행과 S3 I/O는 수초 걸릴 수 있는 외부 프로세스 호출이라, claim()/complete() 사이의
	// DB 트랜잭션 밖에서 수행한다 — 커넥션을 그 시간만큼 붙잡아두지 않기 위함.
	//
	// claim()/complete()와 같은 클래스에 있지만 여기서 this.claim()/this.complete()를 직접 부르지
	// 않는다 — 스프링 프록시 기반 @Transactional은 "다른 빈을 통해 호출될 때만" 적용되는데, 같은
	// 클래스 안에서 this로 호출하면 프록시를 우회해 트랜잭션이 아예 시작되지 않는다. 그 상태로
	// claimForProcessing 같은 @Modifying 쿼리를 실행하면 "No active transaction for update or
	// delete query" 에러가 난다(실제로 로컬 검증 중 재현됨). 그래서 claim()/complete()는 반드시
	// PlantTimelapseWorker처럼 외부(다른 빈)에서 호출해야 한다.
	// previousVideoUrl은 DB에서 다시 읽지 않고 호출부(PlantTimelapseRequestedEvent)가 넘겨준 값을
	// 그대로 쓴다 — 재요청 시점에 PlantTimelapse.restart()가 이미 이 행의 videoUrl을 null로 지워버려서,
	// 여기서 findByPlantProfileId로 다시 조회하면 항상 null만 보이기 때문이다(실제 로컬 검증 중
	// 재생성해도 이전 영상이 S3에서 안 지워지는 버그로 발견됨).
	public String encodeAndUpload(Long profileId, String previousVideoUrl) {
		List<JournalImage> images = journalImageRepository.findRepresentativeByProfileIdOrderByWrittenDateAsc(profileId);
		// 요청 검증(대표이미지 2장 이상) 시점과 이 워커가 실제로 도는 시점(비동기) 사이에 사용자가
		// 일지를 지울 수 있어, 여기서 다시 비어있을 수 있다 — images.get(0)에서 그냥 터지게
		// 두지 않고 명확한 실패로 처리한다.
		if (images.isEmpty()) {
			throw new TimelapseEncodingException("No representative images available for profileId=" + profileId);
		}
		// S3 다운로드는 이미지별로 독립적인 네트워크 I/O라 순차로 하나씩 기다릴 필요가 없다 —
		// 모두 동시에 요청을 걸어두고 순서대로 join()해 결과를 모은다(순서는 images 순서 그대로
		// join()하므로 유지된다). 반드시 전용 downloadExecutor를 쓴다 — 기본 ForkJoinPool.commonPool()은
		// 앱 전체가 공유하는 자원이라 여기서 오래 붙잡으면 무관한 기능도 느려지고, timelapseTaskExecutor는
		// 코어/맥스 1이라(지금 이 코드를 실행 중인 스레드가 바로 그 1개) 재사용하면 데드락이 난다.
		List<CompletableFuture<TimelapseSourceImage>> downloads = images.stream()
				.map(image -> CompletableFuture.supplyAsync(() -> new TimelapseSourceImage(
						journalImageUploadService.downloadBytes(image.getImageUrl()),
						extensionOf(image.getImageUrl())), downloadExecutor))
				.toList();
		List<TimelapseSourceImage> sources = downloads.stream().map(this::joinUnwrapped).toList();
		byte[] videoBytes = encoder.encode(sources);

		Long ownerId = images.get(0).getUser().getId();
		String videoUrl = videoStorageService.uploadVideo(ownerId, videoBytes);

		if (previousVideoUrl != null) {
			videoStorageService.deleteVideo(previousVideoUrl);
		}

		return videoUrl;
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

	// 복구 스케줄러(PlantTimelapseRecoveryScheduler) 전용 — 정상 워커가 죽거나 fail() 자체가
	// 실패해서 PROCESSING에 갇히거나, 실행 큐가 가득 차 제출 자체가 거부돼 PENDING에 갇힌 행을
	// 강제로 FAILED 처리한다. failIfStillPendingOrProcessing이 여전히 그 상태일 때만 조건부로
	// 전이시키므로, 그 사이 정상 워커가 이미 완료/실패 처리했다면 0을 반환하고 여기서는
	// 아무것도(중복 알림 포함) 하지 않는다.
	@Transactional
	public void recoverStale(Long profileId) {
		int updated = plantTimelapseRepository.failIfStillPendingOrProcessing(profileId, "REQUEST_TIMEOUT", LocalDateTime.now(KST));
		if (updated == 0) {
			return;
		}
		PlantTimelapse timelapse = plantTimelapseRepository.findByPlantProfileId(profileId).orElseThrow();
		notify(timelapse, "타임랩스 생성에 실패했어요", "타임랩스 영상 생성 중 문제가 발생했어요. 다시 시도해 주세요.");
	}

	private void notify(PlantTimelapse timelapse, String title, String content) {
		Long ownerId = timelapse.getPlantProfile().getUser().getId();
		notificationService.notify(ownerId, NotificationType.TIMELAPSE, title, content,
				"/plants/" + timelapse.getPlantProfile().getId(), "PLANT_TIMELAPSE", timelapse.getPlantProfile().getId());
	}

	// CompletableFuture.join()은 원본 예외를 CompletionException으로 감싸서 던지는데, 그대로 두면
	// PlantTimelapseWorker.failReasonOf()가 사용자에게 보여줄 실패 사유가
	// "java.lang.RuntimeException: ..." 식으로 지저분해진다 — 원본 예외를 그대로 다시 던져 기존
	// 실패 메시지 형식을 유지한다.
	private TimelapseSourceImage joinUnwrapped(CompletableFuture<TimelapseSourceImage> future) {
		try {
			return future.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw exception;
		}
	}

	private String extensionOf(String imageUrl) {
		int dot = imageUrl.lastIndexOf('.');
		return dot >= 0 ? imageUrl.substring(dot) : ".jpg";
	}
}
