package com.kiwobollae.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.content.dto.request.PlantSpeciesRequest;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantSpeciesManagementServiceTest {

	@Mock private PlantSpeciesRepository plantSpeciesRepository;

	@InjectMocks
	private PlantSpeciesManagementService plantSpeciesManagementService;

	@Test
	void createSpeciesSavesAndReturnsResponse() {
		PlantSpeciesRequest request = new PlantSpeciesRequest("몬스테라", "관엽식물", "적당량의 물을 준다");
		given(plantSpeciesRepository.save(any(PlantSpecies.class))).willAnswer(invocation -> {
			PlantSpecies species = invocation.getArgument(0);
			ReflectionTestUtils.setField(species, "id", 1L);
			return species;
		});

		PlantSpeciesResponse result = plantSpeciesManagementService.createSpecies(request);

		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.name()).isEqualTo("몬스테라");
		assertThat(result.category()).isEqualTo("관엽식물");
		assertThat(result.careGuide()).isEqualTo("적당량의 물을 준다");
	}
}
