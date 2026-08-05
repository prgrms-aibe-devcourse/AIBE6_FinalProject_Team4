package com.kiwobollae.api.ai.guide;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantCareGuideCacheRepository extends JpaRepository<PlantCareGuideCache, Long> {

  Optional<PlantCareGuideCache> findBySpeciesNameAndGuideVersion(
      String speciesName, int guideVersion);
}
