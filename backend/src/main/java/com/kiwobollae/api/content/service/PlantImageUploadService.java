package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.dto.response.PlantImageUploadResponse;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Bucket is private — mirrors {@link JournalImageUploadService}'s upload/serve
 * pattern, but stores objects under the "plant_profile" prefix instead of
 * "journals" so plant profile photos don't mix with journal images in S3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantImageUploadService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/plants/images/";

	private final S3Client s3Client;

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
			return ResponseEntity.ok()
					.contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
					.body(bytes);
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (SdkException | IOException e) {
			throw new BusinessException(ErrorCode.PLANT_IMAGE_UPLOAD_FAILED);
		}
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
