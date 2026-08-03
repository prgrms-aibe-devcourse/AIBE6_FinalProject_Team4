package com.kiwobollae.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.content.dto.request.PlantSpeciesRequest;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.Optional;
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

	@Test
	void updateSpeciesModifiesAndReturnsResponse() {
		PlantSpecies species = PlantSpecies.builder().name("몬스테라").category("관엽식물").careGuide("물을 준다").build();
		ReflectionTestUtils.setField(species, "id", 1L);
		given(plantSpeciesRepository.findById(1L)).willReturn(Optional.of(species));
		PlantSpeciesRequest request = new PlantSpeciesRequest("몬스테라 디럭스", "대형 관엽식물", "물을 자주 준다");

		PlantSpeciesResponse result = plantSpeciesManagementService.updateSpecies(1L, request);

		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.name()).isEqualTo("몬스테라 디럭스");
		assertThat(result.category()).isEqualTo("대형 관엽식물");
		assertThat(result.careGuide()).isEqualTo("물을 자주 준다");
	}

	@Test
	void updateSpeciesKeepsExistingCategoryAndCareGuideWhenOmitted() {
		PlantSpecies species = PlantSpecies.builder().name("몬스테라").category("관엽식물").careGuide("적당량의 물을 준다").build();
		ReflectionTestUtils.setField(species, "id", 1L);
		given(plantSpeciesRepository.findById(1L)).willReturn(Optional.of(species));
		PlantSpeciesRequest request = new PlantSpeciesRequest("몬스테라 디럭스", null, null);

		PlantSpeciesResponse result = plantSpeciesManagementService.updateSpecies(1L, request);

		assertThat(result.name()).isEqualTo("몬스테라 디럭스");
		assertThat(result.category()).isEqualTo("관엽식물");
		assertThat(result.careGuide()).isEqualTo("적당량의 물을 준다");
	}

	@Test
	void updateSpeciesThrowsWhenNotFound() {
		given(plantSpeciesRepository.findById(99L)).willReturn(Optional.empty());
		PlantSpeciesRequest request = new PlantSpeciesRequest("몬스테라", null, null);

		assertThatThrownBy(() -> plantSpeciesManagementService.updateSpecies(99L, request))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void createSpeciesThrowsWhenNameAlreadyExists() {
		given(plantSpeciesRepository.existsByName("몬스테라")).willReturn(true);
		PlantSpeciesRequest request = new PlantSpeciesRequest("몬스테라", null, null);

		assertThatThrownBy(() -> plantSpeciesManagementService.createSpecies(request))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void updateSpeciesThrowsWhenNameAlreadyUsedByAnotherSpecies() {
		PlantSpecies species = PlantSpecies.builder().name("몬스테라").category(null).careGuide(null).build();
		ReflectionTestUtils.setField(species, "id", 1L);
		given(plantSpeciesRepository.findById(1L)).willReturn(Optional.of(species));
		given(plantSpeciesRepository.existsByNameAndIdNot("바질", 1L)).willReturn(true);
		PlantSpeciesRequest request = new PlantSpeciesRequest("바질", null, null);

		assertThatThrownBy(() -> plantSpeciesManagementService.updateSpecies(1L, request))
				.isInstanceOf(BusinessException.class);
	}
}
