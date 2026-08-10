package com.kiwobollae.api.timelapse.service;

import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

	// 콤마로 여러 구간을 나열하는 멀티 Range(bytes=0-0,100-200)는 지원하지 않는다 — S3가 그런
	// 요청엔 Content-Type을 multipart/byteranges로 바꿔 응답하는데, 우리는 항상 video/mp4로
	// 고정해서 내려주므로 클라이언트가 못 읽는다. RFC 7233상 서버가 Range를 무시하고 200 전체
	// 응답을 줘도 되므로, 단일 구간이 아니면 그냥 무시하고 전체를 서빙한다.
	private static final Pattern SINGLE_RANGE = Pattern.compile("^bytes=\\d*-\\d*$");

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

	// rangeHeader가 있으면(브라우저 <video> 탐색 등) S3에도 그대로 Range를 전달해 필요한 구간만
	// 받아온다. S3가 Range를 받아들이면 Content-Range가 채워진 응답을 주므로 그걸 기준으로
	// 200/206을 결정한다(요청했다고 항상 206이 오는 건 아니라서 rangeHeader 유무가 아니라 실제
	// 응답을 본다). 응답 바디는 byte[]로 모아서 반환하지 않고 StreamingResponseBody로 그대로
	// 흘려보낸다 — bytes=0-처럼 끝을 지정하지 않은(=사실상 전체 파일) Range도 있을 수 있는데,
	// byte[]로 모으면 그 경우 "메모리에 안 올리려는" 목적이 그대로 무너지기 때문이다.
	public ResponseEntity<StreamingResponseBody> download(Long userId, String filename, String rangeHeader) {
		String key = objectKey(userId, filename);
		String singleRange = SINGLE_RANGE.matcher(rangeHeader == null ? "" : rangeHeader).matches() ? rangeHeader : null;
		GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
		if (singleRange != null) {
			request.range(singleRange);
		}

		ResponseInputStream<GetObjectResponse> object;
		try {
			object = s3Client.getObject(request.build());
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		} catch (S3Exception e) {
			// S3가 요청한 구간이 실제 파일 범위를 벗어난다고 판단하면 InvalidRange(416)를 준다 —
			// 이걸 업로드 실패(502)로 뭉뚱그리지 않고 그대로 416으로 전달한다.
			if (e.statusCode() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
				return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
			}
			throw new BusinessException(ErrorCode.TIMELAPSE_VIDEO_UPLOAD_FAILED);
		} catch (SdkException e) {
			throw new BusinessException(ErrorCode.TIMELAPSE_VIDEO_UPLOAD_FAILED);
		}

		GetObjectResponse metadata = object.response();
		boolean partial = metadata.contentRange() != null;
		ResponseEntity.BodyBuilder response = ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
				.contentType(MediaType.parseMediaType("video/mp4"))
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Disposition", "inline")
				.header("Cache-Control", "private, max-age=31536000, immutable")
				.header(HttpHeaders.ACCEPT_RANGES, "bytes");
		if (partial) {
			response.header(HttpHeaders.CONTENT_RANGE, metadata.contentRange());
		}
		if (metadata.contentLength() != null) {
			response.contentLength(metadata.contentLength());
		}
		// StreamingResponseBody의 writeTo()는 이 메서드가 반환된 뒤, 응답을 실제로 커밋할 때
		// 별도 스레드에서 호출된다 — 그래서 object를 여기서 try-with-resources로 닫지 않고
		// writeTo() 안에서 닫는다(여기서 닫으면 스트리밍 시점엔 이미 끊긴 스트림이 된다).
		StreamingResponseBody body = outputStream -> {
			try (object) {
				object.transferTo(outputStream);
			} catch (IOException e) {
				log.warn("Failed to stream timelapse video. key={}", key, e);
			}
		};
		return response.body(body);
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
