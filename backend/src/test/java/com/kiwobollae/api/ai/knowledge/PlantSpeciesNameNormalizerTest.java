package com.kiwobollae.api.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlantSpeciesNameNormalizerTest {

  private final PlantSpeciesNameNormalizer normalizer = new PlantSpeciesNameNormalizer();

  @Test
  void normalizesUnicodeWhitespaceAndCaseForSharedLookup() {
    PlantSpeciesNameNormalizer.NormalizedSpeciesName normalized =
        normalizer.normalize("  Ｏｃｉｍｕｍ\tBASILICUM  ");

    assertThat(normalized.cacheName()).isEqualTo("OcimumBASILICUM");
    assertThat(normalized.lookupKey()).isEqualTo("ocimumbasilicum");
  }

  @Test
  void rejectsBlankAndOverlongSpeciesNames() {
    assertThatThrownBy(() -> normalizer.normalize("  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> normalizer.normalize("가".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
