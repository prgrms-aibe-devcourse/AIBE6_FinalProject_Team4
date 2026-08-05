package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.dto.response.JournalImageUploadResponse;
import com.kiwobollae.api.content.repository.JournalImageRepository;
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
 * 버킷은 private이라 이미지를 raw S3 URL로 직접 서빙하지 않는다. 업로드 시 객체를 S3에
 * 쓰고 {@link com.kiwobollae.api.content.controller.JournalImageUploadController#serveImage}로
 * 되돌아오는 URL을 반환하며, 그 엔드포인트가 (우리 S3Client 자격증명으로) S3에서 바이트를
 * 가져와 클라이언트로 스트리밍한다. 즉 브라우저는 S3와 직접 통신하지 않는다.
 *
 * <p>반환/저장되는 URL은 host-relative다(예: "/api/v1/journals/images/..."). 업로드 시점의
 * 요청 scheme/host/port를 그대로 박아넣으면 JournalImage row에 영구히 저장돼버려서, 한 환경에서는
 * 문제없지만 앱이 다른 호스트로 옮겨가는 순간(로컬 개발 → 실제 배포 등) 깨지고 데이터 마이그레이션이
 * 필요해진다. 호출부(프론트엔드)가 렌더링 시점에 자신의 환경에 맞는 base URL을 앞에 붙인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalImageUploadService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
	// upload()이 만드는 서빙 URL의 경로 부분 — 저장된 imageUrl에서 S3 key(journals/{userId}/{filename})를
	// 되짚어내는 데 쓴다.
	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/journals/images/";

	private final S3Client s3Client;
	private final JournalImageRepository journalImageRepository;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public JournalImageUploadResponse upload(MultipartFile file, Long userId) {
		if (file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_INVALID_TYPE);
		}

		byte[] content = readBytes(file);
		String extension = extensionOf(file);
		if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_INVALID_TYPE);
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
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_UPLOAD_FAILED);
		}

		String url = SERVE_PATH_MARKER + userId + "/" + filename;
		return new JournalImageUploadResponse(url, hash);
	}

	/**
	 * 더 이상 어떤 일지에서도 참조되지 않는 이미지에 대한 best-effort S3 정리(수정 시 교체됐거나,
	 * 일지 자체가 삭제된 경우). 절대 예외를 던지지 않는다 — 정리 실패가 이를 유발한 일지
	 * 작성/삭제 자체를 실패시키면 안 되고, 그냥 고아 객체를 남겨두고 나중에 처리한다.
	 *
	 * <p>{@code ownerUserId}는 이 호출을 유발한 일지 작업의 userId여야 한다 — key에 박혀 있는
	 * (저장된 URL에서 다시 파싱해낸) userId와 일치하지 않으면 삭제가 거부된다. 현재는 이미
	 * 소유권 검증을 마친 호출부(PlantJournalService 참고)에서만 이 메서드를 부르지만, 앞으로의
	 * 모든 호출부를 신뢰하기보다 여기서도 검증을 강제한다 — 이 URL/key 쌍 자체는 소유권을
	 * 증명하지 않는다.
	 */
	public void delete(String imageUrl, Long ownerUserId) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
			return;
		}
		if (!isOwnedBy(key, ownerUserId)) {
			log.warn("Refused to delete journal image not owned by user {}: {}", ownerUserId, key);
			return;
		}
		// 아직 어떤 일지에 쓰이고 있다면 지우지 않는다 — DB 참조가 남은 채로 S3 객체만 사라지면
		// 영구히 깨진 이미지가 된다. 이 호출부보다 앞서 참조를 이미 지운 경우(updateJournal/
		// deleteJournal)에는 항상 false라 정상적으로 삭제가 진행된다.
		if (journalImageRepository.existsByImageUrl(imageUrl)) {
			log.warn("Refused to delete journal image still referenced by a journal: {}", key);
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete orphaned journal image from S3: {}", key, e);
		}
	}

	private String keyFromUrl(String imageUrl) {
		int idx = imageUrl.indexOf(SERVE_PATH_MARKER);
		if (idx < 0) {
			return null;
		}
		return "journals/" + imageUrl.substring(idx + SERVE_PATH_MARKER.length());
	}

	// key 형태는 항상 "journals/{userId}/{filename}" — 두 번째 세그먼트가 소유자다.
	private boolean isOwnedBy(String key, Long ownerUserId) {
		String[] parts = key.split("/");
		return parts.length == 3 && parts[1].equals(String.valueOf(ownerUserId));
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
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_UPLOAD_FAILED);
		}
	}

	// 타임랩스 워커가 대표이미지를 인코딩용으로 내려받을 때 쓰는 내부 전용 메서드.
	// download(userId, filename)과 달리 HTTP 응답 형태가 아니라 순수 바이트만 반환한다.
	public byte[] downloadBytes(String imageUrl) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		}
		try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
				GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			return object.readAllBytes();
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (SdkException | IOException e) {
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_UPLOAD_FAILED);
		}
	}

	private String objectKey(Long userId, String filename) {
		return "journals/" + userId + "/" + filename;
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_UPLOAD_FAILED);
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
			throw new BusinessException(ErrorCode.JOURNAL_IMAGE_UPLOAD_FAILED);
		}
	}
}
