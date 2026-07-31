package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.dto.response.PlantImageUploadResponse;
import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 버킷이 private이므로 {@link JournalImageUploadService}의 업로드/서빙 패턴을 그대로 따르되,
 * S3에서 일지 이미지와 섞이지 않도록 "journals" 대신 "plant_profile" 접두사로 객체를 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantImageUploadService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/plants/images/";

	private final S3Client s3Client;
	private final PlantProfileRepository plantProfileRepository;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public PlantImageUploadResponse upload(MultipartFile file, Long userId) {
		if (file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_INVALID_TYPE);
		}

		byte[] content = readBytes(file);
		String extension = extensionOf(file);
		if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_INVALID_TYPE);
		}

		String hash = sha256Hex(content);
		String filename = UUID.randomUUID() + extension;
		String key = objectKey(userId, filename);

		try {
			s3Client.putObject(
					PutObjectRequest.builder()
							.bucket(bucket)
							.key(key)
							.contentType(file.getContentType())
							.build(),
					RequestBody.fromBytes(content));
		} catch (SdkException e) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		}

		String url = SERVE_PATH_MARKER + userId + "/" + filename;
		return new PlantImageUploadResponse(url, hash);
	}

	public ResponseEntity<byte[]> download(Long userId, String filename) {
		String key = objectKey(userId, filename);
		try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
				GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			byte[] bytes = object.readAllBytes();
			String contentType = object.response().contentType();
			// 파일명은 upload()가 부여한 UUID라 객체 내용이 불변이므로 무기한 캐시해도 안전하다.
			// nosniff + inline은 API와 같은 오리진에서 서빙되는 사용자 업로드 바이트의 콘텐츠 스니핑 경로를 막는다.
			return ResponseEntity.ok()
					.contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
					.header("X-Content-Type-Options", "nosniff")
					.header("Content-Disposition", "inline")
					.header("Cache-Control", "private, max-age=31536000, immutable")
					.body(bytes);
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (SdkException | IOException e) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		}
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
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete orphaned plant image from S3: {}", key, e);
		}
	}

	private String keyFromUrl(String imageUrl) {
		int idx = imageUrl.indexOf(SERVE_PATH_MARKER);
		if (idx < 0) {
			return null;
		}
		return "plant_profile/" + imageUrl.substring(idx + SERVE_PATH_MARKER.length());
	}

	// key 형태는 항상 "plant_profile/{userId}/{filename}" — 두 번째 세그먼트가 소유자다.
	private boolean isOwnedBy(String key, Long ownerUserId) {
		String[] parts = key.split("/");
		return parts.length == 3 && parts[1].equals(String.valueOf(ownerUserId));
	}

	private String objectKey(Long userId, String filename) {
		return "plant_profile/" + userId + "/" + filename;
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		}
	}

	private String extensionOf(MultipartFile file) {
		String original = file.getOriginalFilename();
		int dot = original == null ? -1 : original.lastIndexOf('.');
		return dot >= 0 ? original.substring(dot) : "";
	}

	private String sha256Hex(byte[] content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content));
		} catch (NoSuchAlgorithmException e) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		}
	}
}
