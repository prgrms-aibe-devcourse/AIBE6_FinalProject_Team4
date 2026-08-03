package com.kiwobollae.api.admin.service;

import com.kiwobollae.api.content.dto.request.PlantSpeciesRequest;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlantSpeciesManagementService {

	private final PlantSpeciesRepository plantSpeciesRepository;

	@Transactional
	public PlantSpeciesResponse createSpecies(PlantSpeciesRequest request) {
		PlantSpecies species = PlantSpecies.builder()
				.name(request.name())
				.category(request.category())
				.careGuide(request.careGuide())
				.build();
		return PlantSpeciesResponse.from(plantSpeciesRepository.save(species));
	}
}
