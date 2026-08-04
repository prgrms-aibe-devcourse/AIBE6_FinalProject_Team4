package com.kiwobollae.api.content.service;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
 * 이미지 서빙(PlantImageUploadService/JournalImageUploadService)과 동일한 패턴 —
 * private 버킷을 대신 프록시하며, 파일명은 UUID라 인증 없이 permitAll로 서빙해도
 * 추측 불가능하다. profileId 같은 순차 정수를 파일명에 쓰지 않는다(무단 열람 방지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlantTimelapseVideoStorageService {

	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/plants/timelapse-videos/";

	private final S3Client s3Client;

	@Value("${aws.s3.bucket}")
	private String bucket;

	public String uploadVideo(Long userId, byte[] videoBytes) {
		String filename = UUID.randomUUID() + ".mp4";
		String key = objectKey(userId, filename);
		try {
			s3Client.putObject(
					PutObjectRequest.builder().bucket(bucket).key(key).contentType("video/mp4").build(),
					RequestBody.fromBytes(videoBytes));
		} catch (SdkException e) {
			throw new BusinessException(ErrorCode.TIMELAPSE_VIDEO_UPLOAD_FAILED);
		}
		return SERVE_PATH_MARKER + userId + "/" + filename;
	}

	// 재생성 시 이전 영상을 정리하는 best-effort 삭제 — 실패해도 새 영상 반영 자체를 막지 않는다.
	public void deleteVideo(String videoUrl) {
		String key = keyFromUrl(videoUrl);
		if (key == null) {
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete previous timelapse video from S3: {}", key, e);
		}
	}

	public ResponseEntity<byte[]> download(Long userId, String filename) {
		String key = objectKey(userId, filename);
		try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
				GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			byte[] bytes = object.readAllBytes();
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("video/mp4"))
					.header("X-Content-Type-Options", "nosniff")
					.header("Content-Disposition", "inline")
					.header("Cache-Control", "private, max-age=31536000, immutable")
					.body(bytes);
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (SdkException | IOException e) {
			throw new BusinessException(ErrorCode.TIMELAPSE_VIDEO_UPLOAD_FAILED);
		}
	}

	private String objectKey(Long userId, String filename) {
		return "plant_profile_timelapse/" + userId + "/" + filename;
	}

	private String keyFromUrl(String videoUrl) {
		int idx = videoUrl.indexOf(SERVE_PATH_MARKER);
		if (idx < 0) {
			return null;
		}
		return "plant_profile_timelapse/" + videoUrl.substring(idx + SERVE_PATH_MARKER.length());
	}
}
