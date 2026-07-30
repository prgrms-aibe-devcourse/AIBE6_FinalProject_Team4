package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.dto.response.JournalImageUploadResponse;
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
 * Bucket is private — images aren't served via a raw S3 URL. Upload writes the
 * object to S3 and returns a URL pointing back at {@link
 * com.kiwobollae.api.content.controller.JournalImageUploadController#serveImage},
 * which fetches the bytes from S3 (using our S3Client credentials) and streams
 * them to the client. So the browser never talks to S3 directly.
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

	@Value("${aws.s3.bucket}")
	private String bucket;

	public JournalImageUploadResponse upload(MultipartFile file, Long userId, String baseUrl) {
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

		String url = baseUrl + SERVE_PATH_MARKER + userId + "/" + filename;
		return new JournalImageUploadResponse(url, hash);
	}

	/**
	 * Best-effort S3 cleanup for an image that's no longer referenced by any
	 * journal (replaced on update, or the journal itself was deleted). Never
	 * throws — a failed cleanup shouldn't fail the journal write/delete that
	 * triggered it; it just leaves an orphaned object to be dealt with later.
	 */
	public void delete(String imageUrl) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
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

	public ResponseEntity<byte[]> download(Long userId, String filename) {
		String key = objectKey(userId, filename);
		try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
				GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			byte[] bytes = object.readAllBytes();
			String contentType = object.response().contentType();
			return ResponseEntity.ok()
					.contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
					.body(bytes);
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
