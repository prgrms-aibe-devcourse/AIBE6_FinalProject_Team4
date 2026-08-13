package com.kiwobollae.api.board.service;

import com.kiwobollae.api.board.dto.response.BoardImageUploadResponse;
import com.kiwobollae.api.board.repository.BoardPostImageRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
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
 * JournalImageUploadService와 동일한 패턴 — 버킷이 private이라 서버가 대신 스트리밍 서빙한다.
 * 브라우저는 S3와 직접 통신하지 않고, 저장/반환되는 URL은 host-relative다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardImageUploadService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/board/images/";

	private final S3Client s3Client;
	private final BoardPostImageRepository boardPostImageRepository;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public BoardImageUploadResponse upload(MultipartFile file, Long userId) {
		if (file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new BusinessException(ErrorCode.BOARD_IMAGE_INVALID_TYPE);
		}

		byte[] content = readBytes(file);
		String extension = extensionOf(file);
		if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
			throw new BusinessException(ErrorCode.BOARD_IMAGE_INVALID_TYPE);
		}

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
			throw new BusinessException(ErrorCode.BOARD_IMAGE_UPLOAD_FAILED);
		}

		return new BoardImageUploadResponse(SERVE_PATH_MARKER + userId + "/" + filename);
	}

	/**
	 * 더 이상 어떤 게시글에서도 참조되지 않는 이미지에 대한 best-effort S3 정리. 절대 예외를
	 * 던지지 않는다 — 정리 실패가 이를 유발한 게시글 작성/수정/삭제 자체를 실패시키면 안 된다.
	 */
	public void delete(String imageUrl, Long ownerUserId) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
			return;
		}
		if (!isOwnedBy(key, ownerUserId)) {
			log.warn("Refused to delete board image not owned by user {}: {}", ownerUserId, key);
			return;
		}
		if (boardPostImageRepository.existsByImageUrl(imageUrl)) {
			log.warn("Refused to delete board image still referenced by a post: {}", key);
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete orphaned board image from S3: {}", key, e);
		}
	}

	private String keyFromUrl(String imageUrl) {
		int idx = imageUrl.indexOf(SERVE_PATH_MARKER);
		if (idx < 0) {
			return null;
		}
		return "board/" + imageUrl.substring(idx + SERVE_PATH_MARKER.length());
	}

	// key 형태는 항상 "board/{userId}/{filename}" — 두 번째 세그먼트가 소유자다.
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
			return ResponseEntity.ok()
					.contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
					.header("X-Content-Type-Options", "nosniff")
					.header("Content-Disposition", "inline")
					.header("Cache-Control", "private, max-age=31536000, immutable")
					.body(bytes);
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (SdkException | IOException e) {
			throw new BusinessException(ErrorCode.BOARD_IMAGE_UPLOAD_FAILED);
		}
	}

	private String objectKey(Long userId, String filename) {
		return "board/" + userId + "/" + filename;
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.BOARD_IMAGE_UPLOAD_FAILED);
		}
	}

	private String extensionOf(MultipartFile file) {
		String original = file.getOriginalFilename();
		int dot = original == null ? -1 : original.lastIndexOf('.');
		return dot >= 0 ? original.substring(dot) : "";
	}
}
