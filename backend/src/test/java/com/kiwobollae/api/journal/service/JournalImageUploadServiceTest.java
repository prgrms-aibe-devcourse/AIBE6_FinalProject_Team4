package com.kiwobollae.api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.journal.repository.JournalImageRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import java.io.ByteArrayInputStream;

import com.kiwobollae.api.plantProfile.service.JournalImageUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@ExtendWith(MockitoExtension.class)
class JournalImageUploadServiceTest {

	private static final String IMAGE_URL = ApiVersion.V1 + "/journals/images/7/photo.jpg";

	@Mock private S3Client s3Client;
	@Mock private JournalImageRepository journalImageRepository;

	@InjectMocks
	private JournalImageUploadService journalImageUploadService;

	@Test
	void deleteRefusesWhenNotOwned() {
		journalImageUploadService.delete(IMAGE_URL, 99L);

		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void deleteRefusesWhenStillReferenced() {
		given(journalImageRepository.existsByImageUrl(IMAGE_URL)).willReturn(true);

		journalImageUploadService.delete(IMAGE_URL, 7L);

		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void deleteProceedsWhenOwnedAndNotReferenced() {
		given(journalImageRepository.existsByImageUrl(IMAGE_URL)).willReturn(false);

		journalImageUploadService.delete(IMAGE_URL, 7L);

		verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) -> req.key().equals("journals/7/photo.jpg")));
	}

	@Test
	void downloadBytesReturnsObjectContent() {
		byte[] content = "fake-image-bytes".getBytes();
		given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(fakeResponse(content));

		byte[] result = journalImageUploadService.downloadBytes(IMAGE_URL);

		assertThat(result).isEqualTo(content);
	}

	private ResponseInputStream<GetObjectResponse> fakeResponse(byte[] content) {
		GetObjectResponse response = GetObjectResponse.builder().contentType("image/jpeg").build();
		return new ResponseInputStream<>(response, AbortableInputStream.create(new ByteArrayInputStream(content)));
	}
}
