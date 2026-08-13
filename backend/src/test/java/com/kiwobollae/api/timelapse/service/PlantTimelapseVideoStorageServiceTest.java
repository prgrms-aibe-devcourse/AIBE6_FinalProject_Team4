package com.kiwobollae.api.timelapse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.global.common.ApiVersion;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class PlantTimelapseVideoStorageServiceTest {

	@Mock private S3Client s3Client;

	@InjectMocks
	private PlantTimelapseVideoStorageService videoStorageService;

	@org.junit.jupiter.api.BeforeEach
	void setBucket() {
		ReflectionTestUtils.setField(videoStorageService, "bucket", "test-bucket");
	}

	@Test
	void uploadVideoPutsObjectAndReturnsServeUrlUnderUserId() {
		String url = videoStorageService.uploadVideo(7L, "video-bytes".getBytes());

		assertThat(url).startsWith(ApiVersion.V1 + "/plants/timelapse-videos/7/");
		assertThat(url).endsWith(".mp4");
		verify(s3Client).putObject(argThat((PutObjectRequest req) ->
				req.bucket().equals("test-bucket") && req.key().startsWith("plant_profile_timelapse/7/")), any(RequestBody.class));
	}

	@Test
	void deleteVideoDeletesCorrespondingS3Object() {
		String url = ApiVersion.V1 + "/plants/timelapse-videos/7/old-uuid.mp4";

		videoStorageService.deleteVideo(url);

		verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
				req.key().equals("plant_profile_timelapse/7/old-uuid.mp4")));
	}

	private static byte[] writeBody(org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		body.writeTo(out);
		return out.toByteArray();
	}

	@Test
	void downloadReturnsVideoBytesWithMp4ContentType() throws Exception {
		byte[] content = "video-bytes".getBytes();
		GetObjectResponse response = GetObjectResponse.builder().build();
		given(s3Client.getObject(any(GetObjectRequest.class)))
				.willReturn(new ResponseInputStream<>(response, AbortableInputStream.create(new java.io.ByteArrayInputStream(content))));

		var result = videoStorageService.download(7L, "abc.mp4", null);

		assertThat(result.getStatusCode().value()).isEqualTo(200);
		assertThat(writeBody(result.getBody())).isEqualTo(content);
		assertThat(result.getHeaders().getContentType().toString()).isEqualTo("video/mp4");
		assertThat(result.getHeaders().getFirst("Accept-Ranges")).isEqualTo("bytes");
	}

	@Test
	void downloadWithRangeHeaderForwardsRangeToS3AndReturnsPartialContent() throws Exception {
		byte[] chunk = "chunk".getBytes();
		GetObjectResponse response = GetObjectResponse.builder().contentRange("bytes 0-4/11").build();
		given(s3Client.getObject(argThat((GetObjectRequest req) -> "bytes=0-4".equals(req.range()))))
				.willReturn(new ResponseInputStream<>(response, AbortableInputStream.create(new java.io.ByteArrayInputStream(chunk))));

		var result = videoStorageService.download(7L, "abc.mp4", "bytes=0-4");

		assertThat(result.getStatusCode().value()).isEqualTo(206);
		assertThat(writeBody(result.getBody())).isEqualTo(chunk);
		assertThat(result.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 0-4/11");
	}

	@Test
	void downloadWithMultiRangeHeaderIgnoresRangeAndServesFullContent() throws Exception {
		byte[] content = "video-bytes".getBytes();
		GetObjectResponse response = GetObjectResponse.builder().build();
		given(s3Client.getObject(argThat((GetObjectRequest req) -> req.range() == null)))
				.willReturn(new ResponseInputStream<>(response, AbortableInputStream.create(new java.io.ByteArrayInputStream(content))));

		var result = videoStorageService.download(7L, "abc.mp4", "bytes=0-0,100-200");

		assertThat(result.getStatusCode().value()).isEqualTo(200);
		assertThat(writeBody(result.getBody())).isEqualTo(content);
	}

	@Test
	void downloadWithOutOfRangeHeaderReturns416() {
		given(s3Client.getObject(any(GetObjectRequest.class)))
				.willThrow((S3Exception) S3Exception.builder().statusCode(416).build());

		var result = videoStorageService.download(7L, "abc.mp4", "bytes=999999-9999999");

		assertThat(result.getStatusCode().value()).isEqualTo(416);
	}
}
