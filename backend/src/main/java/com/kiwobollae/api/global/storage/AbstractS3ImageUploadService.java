package com.kiwobollae.api.global.storage;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 버킷이 private인 사용자 업로드 이미지(일지 사진, 식물 대표사진 등)를 위한 S3 업로드/서빙/삭제
 * 공통 로직. 도메인별로 다른 부분(객체 key 접두사, 서빙 URL 경로, 에러 코드)만 생성자로 받고,
 * 참조 카운팅 같은 도메인 고유 삭제 규칙은 하위 클래스가 {@link #deleteObject}를 호출하기 전에
 * 직접 검증한다.
 */
@Slf4j
public abstract class AbstractS3ImageUploadService {

	protected static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	protected static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

	protected record UploadResult(String url, String hash) {
	}

	protected final S3Client s3Client;
	private final String objectPrefix;
	private final String servePathMarker;
	private final ErrorCode invalidTypeError;
	private final ErrorCode uploadFailedError;

	@Value("${aws.s3.bucket}")
	protected String bucket;

	protected AbstractS3ImageUploadService(S3Client s3Client, String objectPrefix, String servePathMarker,
			ErrorCode invalidTypeError, ErrorCode uploadFailedError) {
		this.s3Client = s3Client;
		this.objectPrefix = objectPrefix;
		this.servePathMarker = servePathMarker;
		this.invalidTypeError = invalidTypeError;
		this.uploadFailedError = uploadFailedError;
	}

	protected UploadResult doUpload(MultipartFile file, Long userId) {
		if (file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new BusinessException(invalidTypeError);
		}

		byte[] content = readBytes(file);
		String extension = extensionOf(file);
		if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
			throw new BusinessException(invalidTypeError);
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
			throw new BusinessException(uploadFailedError);
		}

		String url = servePathMarker + userId + "/" + filename;
		return new UploadResult(url, hash);
	}

	protected ResponseEntity<byte[]> download(Long userId, String filename) {
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
			throw new BusinessException(uploadFailedError);
		}
	}

	protected byte[] downloadBytes(String imageUrl) {
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
			throw new BusinessException(uploadFailedError);
		}
	}

	// 더 이상 참조되지 않는 객체에 대한 best-effort S3 정리. 절대 예외를 던지지 않는다 —
	// 정리 실패가 이를 유발한 도메인 쓰기/삭제 자체를 실패시키면 안 되고, 고아 객체는 남겨두고
	// 나중에 처리한다. 호출 전에 소유권/참조 여부는 하위 클래스가 검증해야 한다.
	protected void deleteObject(String key) {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete orphaned S3 object: {}", key, e);
		}
	}

	protected String keyFromUrl(String imageUrl) {
		int idx = imageUrl.indexOf(servePathMarker);
		if (idx < 0) {
			return null;
		}
		return objectPrefix + "/" + imageUrl.substring(idx + servePathMarker.length());
	}

	// key 형태는 항상 "{objectPrefix}/{userId}/{filename}" — 두 번째 세그먼트가 소유자다.
	protected boolean isOwnedBy(String key, Long ownerUserId) {
		String[] parts = key.split("/");
		return parts.length == 3 && parts[1].equals(String.valueOf(ownerUserId));
	}

	protected String objectKey(Long userId, String filename) {
		return objectPrefix + "/" + userId + "/" + filename;
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(uploadFailedError);
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
			throw new BusinessException(uploadFailedError);
		}
	}
}
