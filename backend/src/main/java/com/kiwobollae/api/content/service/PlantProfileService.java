package com.kiwobollae.api.content.service;

import com.kiwobollae.api.content.repository.PlantProfileRepository;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlantProfileService {

	private final PlantProfileRepository plantProfileRepository;
	private final PlantSpeciesRepository plantSpeciesRepository;
}
