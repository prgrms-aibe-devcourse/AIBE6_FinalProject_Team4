package com.kiwobollae.api.plantProfile.service;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.storage.AbstractS3ImageUploadService;
import com.kiwobollae.api.plantProfile.dto.response.PlantImageUploadResponse;
import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 버킷이 private이므로 {@link com.kiwobollae.api.journal.service.JournalImageUploadService}와
 * 같은 업로드/서빙 패턴을 공유하되, S3에서 일지 이미지와 섞이지 않도록 "journals" 대신
 * "plant_profile" 접두사로 객체를 저장한다. S3 공통 로직은 {@link AbstractS3ImageUploadService}에 있다.
 */
@Slf4j
@Service
public class PlantImageUploadService extends AbstractS3ImageUploadService {

	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/plants/images/";

	private final PlantProfileRepository plantProfileRepository;

	public PlantImageUploadService(S3Client s3Client, PlantProfileRepository plantProfileRepository) {
		super(s3Client, "plant_profile", SERVE_PATH_MARKER, ErrorCode.PLANT_IMAGE_INVALID_TYPE, ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		this.plantProfileRepository = plantProfileRepository;
	}

	public PlantImageUploadResponse upload(MultipartFile file, Long userId) {
		UploadResult result = doUpload(file, userId);
		return new PlantImageUploadResponse(result.url(), result.hash());
	}

	public ResponseEntity<byte[]> download(Long userId, String filename) {
		return super.download(userId, filename);
	}

	/**
	 * 더 이상 참조되지 않는 식물 프로필 이미지(프로필 삭제, 또는 수정으로 thumbnailUrl이 교체된 경우)에
	 * 대한 best-effort S3 정리. 절대 예외를 던지지 않는다 — 정리에 실패했다고 해서 이를 유발한 프로필
	 * 쓰기/삭제 자체가 실패해서는 안 되며, 실패 시 고아 객체로 남겨두고 추후 처리한다.
	 *
	 * <p>{@code ownerUserId}는 이 정리를 유발한 프로필 작업의 userId여야 한다 — 저장된 URL에서 다시
	 * 파싱해낸 key에 담긴 userId와 일치하지 않으면 삭제를 거부한다.
	 */
	public void delete(String imageUrl, Long ownerUserId) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
			return;
		}
		if (!isOwnedBy(key, ownerUserId)) {
			log.warn("Refused to delete plant image not owned by user {}: {}", ownerUserId, key);
			return;
		}
		// 아직 어떤 프로필의 대표 사진으로 쓰이고 있다면 지우지 않는다 — DB 참조가 남은 채로 S3
		// 객체만 사라지면 영구히 깨진 이미지가 된다. 이 호출부보다 앞서 참조를 이미 지운 경우
		// (deleteProfile/updateProfile)에는 항상 false라 정상적으로 삭제가 진행된다.
		if (plantProfileRepository.existsByPlantImage(imageUrl)) {
			log.warn("Refused to delete plant image still referenced by a profile: {}", key);
			return;
		}
		deleteObject(key);
	}
}
