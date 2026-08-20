package com.kiwobollae.api.ai.guide.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VerifiedPlantCareKnowledgeRetrieverTest {

  private final VerifiedPlantCareKnowledgeRetriever retriever =
      new VerifiedPlantCareKnowledgeRetriever(
          new ClasspathPlantCareKnowledgeCatalog(new ObjectMapper()));

  @Test
  void retrievesVerifiedSourceForRegisteredSpecies() {
    PlantCareKnowledge knowledge = retriever.retrieve(new PlantCareKnowledgeQuery("방울토마토"));

    assertThat(knowledge.evidence())
        .singleElement()
        .extracting(PlantCareEvidence::sourceId)
        .isEqualTo("nise-cherry-tomato-cultivation");
  }

  @Test
  void matchesRegardlessOfWhitespaceInSpeciesName() {
    PlantCareKnowledge withSpace = retriever.retrieve(new PlantCareKnowledgeQuery("방울 토마토"));
    PlantCareKnowledge withoutSpace = retriever.retrieve(new PlantCareKnowledgeQuery("방울토마토"));

    assertThat(withSpace.fingerprintMaterial()).isEqualTo(withoutSpace.fingerprintMaterial());
    assertThat(withSpace.evidence()).isNotEmpty();
  }

  @Test
  void retrievesVerifiedKnowledgeForEveryDisplayedServiceSpecies() {
    List<String> displayedSpecies = List.of("방울토마토", "바질", "상추", "딸기", "고추", "수박", "당근", "청경채");

    for (String speciesName : displayedSpecies) {
      PlantCareKnowledge knowledge = retriever.retrieve(new PlantCareKnowledgeQuery(speciesName));

      assertThat(knowledge.evidence()).as("verified knowledge for %s", speciesName).isNotEmpty();
    }
  }

  @Test
  void returnsNoKnowledgeWhenNoVerifiedSourceExists() {
    PlantCareKnowledge knowledge = retriever.retrieve(new PlantCareKnowledgeQuery("원숭이꼬리선인장"));

    assertThat(knowledge.isEmpty()).isTrue();
  }
}
