package com.kiwobollae.api.admin.service;

import com.kiwobollae.api.content.dto.request.PlantSpeciesRequest;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlantSpeciesManagementService {

	private final PlantSpeciesRepository plantSpeciesRepository;

	@Transactional
	public PlantSpeciesResponse createSpecies(PlantSpeciesRequest request) {
		if (plantSpeciesRepository.existsByName(request.name())) {
			throw new BusinessException(ErrorCode.PLANT_SPECIES_DUPLICATE_NAME);
		}
		PlantSpecies species = PlantSpecies.builder()
				.name(request.name())
				.category(request.category())
				.careGuide(request.careGuide())
				.build();
		return PlantSpeciesResponse.from(plantSpeciesRepository.save(species));
	}

	@Transactional
	public PlantSpeciesResponse updateSpecies(Long speciesId, PlantSpeciesRequest request) {
		PlantSpecies species = plantSpeciesRepository.findById(speciesId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_SPECIES_NOT_FOUND));
		if (plantSpeciesRepository.existsByNameAndIdNot(request.name(), speciesId)) {
			throw new BusinessException(ErrorCode.PLANT_SPECIES_DUPLICATE_NAME);
		}
		species.updateInfo(request.name(), request.category(), request.careGuide());
		return PlantSpeciesResponse.from(species);
	}
}
