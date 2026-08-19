package com.kiwobollae.api.ai.guide.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VerifiedPlantCareKnowledgeRetrieverTest {

  private final VerifiedPlantCareKnowledgeRetriever retriever =
      new VerifiedPlantCareKnowledgeRetriever(
          new ClasspathPlantCareKnowledgeCatalog(new ObjectMapper()));

  @Test
  void retrievesRegisteredGuideAndVerifiedSourceForSelectedSpeciesOnly() {
    PlantCareKnowledge knowledge =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(
                21L,
                "스위트 바질",
                "HERB",
                "햇빛이 드는 창가에서 겉흙이 마르면 물을 주세요.",
                LocalDateTime.of(2026, 8, 19, 10, 0)));

    assertThat(knowledge.evidence())
        .extracting(PlantCareEvidence::sourceId)
        .containsExactly("plant-species:21:official-care-guide", "nongsaro-218964");
    assertThat(knowledge.evidence().getFirst().sourceUrl())
        .isEqualTo("internal://plant-species/21/care-guide");
    assertThat(knowledge.evidence().get(1).sourceUrl()).contains("nongsaro.go.kr");
  }

  @Test
  void usesVerifiedSourceWhenRegisteredGuideIsMissing() {
    PlantCareKnowledge knowledge =
        retriever.retrieve(new PlantCareKnowledgeQuery(21L, "방울토마토", "FRUIT", "  ", null));

    assertThat(knowledge.evidence())
        .singleElement()
        .extracting(PlantCareEvidence::sourceId)
        .isEqualTo("nise-cherry-tomato-cultivation");
  }

  @Test
  void retrievesVerifiedKnowledgeForEveryDisplayedServiceSpecies() {
    List<String> displayedSpecies = List.of("방울토마토", "바질", "상추", "딸기", "고추", "수박", "당근", "청경채");

    for (String speciesName : displayedSpecies) {
      PlantCareKnowledge knowledge =
          retriever.retrieve(
              new PlantCareKnowledgeQuery(21L, speciesName, "VEGETABLE", "  ", null));

      assertThat(knowledge.evidence()).as("verified knowledge for %s", speciesName).isNotEmpty();
    }
  }

  @Test
  void returnsNoKnowledgeWhenNoRegisteredOrVerifiedSourceExists() {
    PlantCareKnowledge knowledge =
        retriever.retrieve(new PlantCareKnowledgeQuery(21L, "원숭이꼬리선인장", "CACTUS", "  ", null));

    assertThat(knowledge.isEmpty()).isTrue();
  }

  @Test
  void changesFingerprintWhenRegisteredGuideContentChanges() {
    PlantCareKnowledge before =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(21L, "스위트 바질", "HERB", "겉흙이 마르면 물을 주세요.", null));
    PlantCareKnowledge after =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(21L, "스위트 바질", "HERB", "흙이 마르기 전에 물을 주세요.", null));

    assertThat(after.fingerprintMaterial()).isNotEqualTo(before.fingerprintMaterial());
  }
}
