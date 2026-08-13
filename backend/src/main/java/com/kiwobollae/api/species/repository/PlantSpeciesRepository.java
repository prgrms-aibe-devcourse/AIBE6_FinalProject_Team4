package com.kiwobollae.api.species.repository;

import com.kiwobollae.api.species.entity.PlantSpecies;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long> {

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);
}
