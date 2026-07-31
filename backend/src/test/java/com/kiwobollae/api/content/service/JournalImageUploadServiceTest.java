package com.kiwobollae.api.content.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.content.repository.JournalImageRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

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
}
