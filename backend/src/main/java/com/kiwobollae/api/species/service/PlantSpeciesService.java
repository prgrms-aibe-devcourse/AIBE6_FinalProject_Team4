package com.kiwobollae.api.species.service;

import com.kiwobollae.api.species.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.species.entity.PlantSpecies;
import com.kiwobollae.api.species.repository.PlantSpeciesRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlantSpeciesService {

	private final PlantSpeciesRepository plantSpeciesRepository;

	public List<PlantSpeciesResponse> getAllSpecies() {
		return plantSpeciesRepository.findAll().stream()
				.map(PlantSpeciesResponse::from)
				.toList();
	}

	public PlantSpeciesResponse getSpecies(Long speciesId) {
		PlantSpecies species = plantSpeciesRepository.findById(speciesId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLANT_SPECIES_NOT_FOUND));
		return PlantSpeciesResponse.from(species);
	}
}
