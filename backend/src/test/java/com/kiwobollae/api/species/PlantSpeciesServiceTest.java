package com.kiwobollae.api.species;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.species.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;

import com.kiwobollae.api.species.service.PlantSpeciesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlantSpeciesServiceTest {

	@Mock private PlantSpeciesRepository plantSpeciesRepository;

	@InjectMocks
	private PlantSpeciesService plantSpeciesService;

	@Test
	void getAllSpeciesReturnsAllAsResponses() {
		given(plantSpeciesRepository.findAll()).willReturn(List.of(
				species(1L, "몬스테라"),
				species(2L, "선인장")
		));

		List<PlantSpeciesResponse> result = plantSpeciesService.getAllSpecies();

		assertThat(result).hasSize(2)
				.extracting(PlantSpeciesResponse::name)
				.containsExactly("몬스테라", "선인장");
	}

	@Test
	void getSpeciesReturnsMatchingResponse() {
		given(plantSpeciesRepository.findById(1L)).willReturn(Optional.of(species(1L, "몬스테라")));

		PlantSpeciesResponse result = plantSpeciesService.getSpecies(1L);

		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.name()).isEqualTo("몬스테라");
	}

	@Test
	void getSpeciesThrowsWhenNotFound() {
		given(plantSpeciesRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> plantSpeciesService.getSpecies(99L))
				.isInstanceOf(BusinessException.class);
	}

	private PlantSpecies species(Long id, String name) {
		PlantSpecies species = PlantSpecies.builder()
				.name(name)
				.category("관엽식물")
				.careGuide("적당량의 물을 준다")
				.build();
		ReflectionTestUtils.setField(species, "id", id);
		return species;
	}
}
