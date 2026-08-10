package com.kiwobollae.api.plantProfile.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.plantProfile.repository.PlantProfileRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.journal.service.PlantImageUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@ExtendWith(MockitoExtension.class)
class PlantImageUploadServiceTest {

	private static final String IMAGE_URL = ApiVersion.V1 + "/plants/images/7/photo.jpg";

	@Mock private S3Client s3Client;
	@Mock private PlantProfileRepository plantProfileRepository;

	@InjectMocks
	private PlantImageUploadService plantImageUploadService;

	@Test
	void deleteRefusesWhenNotOwned() {
		plantImageUploadService.delete(IMAGE_URL, 99L);

		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void deleteRefusesWhenStillReferenced() {
		given(plantProfileRepository.existsByPlantImage(IMAGE_URL)).willReturn(true);

		plantImageUploadService.delete(IMAGE_URL, 7L);

		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void deleteProceedsWhenOwnedAndNotReferenced() {
		given(plantProfileRepository.existsByPlantImage(IMAGE_URL)).willReturn(false);

		plantImageUploadService.delete(IMAGE_URL, 7L);

		verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) -> req.key().equals("plant_profile/7/photo.jpg")));
	}
}
