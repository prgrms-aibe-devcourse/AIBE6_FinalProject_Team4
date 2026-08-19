package com.kiwobollae.api.ai.guide.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RegisteredOfficialGuideKnowledgeRetrieverTest {

  private final RegisteredOfficialGuideKnowledgeRetriever retriever =
      new RegisteredOfficialGuideKnowledgeRetriever();

  @Test
  void retrievesOnlyTheSelectedSpeciesRegisteredOfficialGuide() {
    PlantCareKnowledge knowledge =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(
                21L,
                "스위트 바질",
                "HERB",
                "햇빛이 드는 창가에서 겉흙이 마르면 물을 주세요.",
                LocalDateTime.of(2026, 8, 19, 10, 0)));

    assertThat(knowledge.evidence())
        .singleElement()
        .satisfies(
            evidence -> {
              assertThat(evidence.sourceId()).isEqualTo("plant-species:21:official-care-guide");
              assertThat(evidence.sourceName()).isEqualTo("서비스 등록 공식 재배 가이드");
              assertThat(evidence.version()).isEqualTo("2026-08-19T10:00:00");
              assertThat(evidence.content()).contains("겉흙이 마르면");
            });
  }

  @Test
  void returnsNoKnowledgeWhenNoVerifiedOfficialGuideIsRegistered() {
    PlantCareKnowledge knowledge =
        retriever.retrieve(new PlantCareKnowledgeQuery(21L, "스위트 바질", "HERB", "  ", null));

    assertThat(knowledge.isEmpty()).isTrue();
  }

  @Test
  void changesFingerprintWhenOfficialGuideContentChanges() {
    PlantCareKnowledge before =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(21L, "스위트 바질", "HERB", "겉흙이 마르면 물을 주세요.", null));
    PlantCareKnowledge after =
        retriever.retrieve(
            new PlantCareKnowledgeQuery(21L, "스위트 바질", "HERB", "흙이 마르기 전에 물을 주세요.", null));

    assertThat(after.fingerprintMaterial()).isNotEqualTo(before.fingerprintMaterial());
  }
}
