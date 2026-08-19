package com.kiwobollae.api.ai.guide.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
