package com.kiwobollae.api.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OfficialDocumentPlantCareKnowledgeRetrieverTest {

  private final PlantSpeciesNameNormalizer normalizer = new PlantSpeciesNameNormalizer();
  private final PlantCareKnowledgeRetriever retriever =
      new OfficialDocumentPlantCareKnowledgeRetriever(
          normalizer, new ClasspathOfficialPlantCareDocumentCorpus(new ObjectMapper(), normalizer));

  @Test
  void retrievesVerifiedOfficialSourceForRegisteredSpecies() {
    PlantCareKnowledge knowledge = retriever.retrieve(new PlantCareKnowledgeQuery("방울토마토"));

    assertThat(knowledge.evidenceStatus()).isEqualTo(PlantCareEvidenceStatus.VERIFIED);
    assertThat(knowledge.resolvedSpeciesName()).isEqualTo("방울토마토");
    assertThat(knowledge.evidence())
        .singleElement()
        .extracting(PlantCareEvidence::sourceId)
        .isEqualTo("nise-cherry-tomato-cultivation");
  }

  @Test
  void resolvesWhitespaceAndScientificNameAliasesToSameCanonicalSpecies() {
    PlantCareKnowledge canonical = retriever.retrieve(new PlantCareKnowledgeQuery("바질"));
    PlantCareKnowledge spacedAlias =
        retriever.retrieve(new PlantCareKnowledgeQuery("  스위트   바질  "));
    PlantCareKnowledge scientificAlias =
        retriever.retrieve(new PlantCareKnowledgeQuery("OCIMUM BASILICUM"));

    assertThat(spacedAlias.requestedSpeciesName()).isEqualTo("스위트바질");
    assertThat(spacedAlias.resolvedSpeciesName()).isEqualTo("바질");
    assertThat(scientificAlias.resolvedSpeciesName()).isEqualTo("바질");
    assertThat(spacedAlias.sourceContextHash()).isEqualTo(canonical.sourceContextHash());
    assertThat(scientificAlias.sourceContextHash()).isEqualTo(canonical.sourceContextHash());
  }

  @Test
  void returnsGeneralFallbackForUnregisteredSpecies() {
    PlantCareKnowledge knowledge = retriever.retrieve(new PlantCareKnowledgeQuery("원숭이꼬리선인장"));

    assertThat(knowledge.evidenceStatus()).isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
    assertThat(knowledge.resolvedSpeciesName()).isEqualTo("원숭이꼬리선인장");
    assertThat(knowledge.evidence()).isEmpty();
  }

  @Test
  void resolvesRegisteredCultivarToBaseSpeciesWithoutFuzzySuffixMatching() {
    PlantCareKnowledge cultivar = retriever.retrieve(new PlantCareKnowledgeQuery("  설향   딸기  "));
    PlantCareKnowledge baseSpecies = retriever.retrieve(new PlantCareKnowledgeQuery("딸기"));
    PlantCareKnowledge unrelatedSuffix = retriever.retrieve(new PlantCareKnowledgeQuery("뱀딸기"));

    assertThat(cultivar.requestedSpeciesName()).isEqualTo("설향딸기");
    assertThat(cultivar.resolvedSpeciesName()).isEqualTo("딸기");
    assertThat(cultivar.speciesMatchType()).isEqualTo(PlantCareSpeciesMatchType.CULTIVAR);
    assertThat(cultivar.grounding().scope()).isEqualTo(PlantCareEvidenceScope.BASE_SPECIES);
    assertThat(cultivar.sourceContextHash()).isEqualTo(baseSpecies.sourceContextHash());
    assertThat(unrelatedSuffix.evidenceStatus())
        .isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
  }

  @Test
  void returnsGeneralFallbackWhenSpeciesMatchesButNoOfficialDocumentExists() {
    PlantCareDocumentCorpus emptyMatchedCorpus =
        query ->
            new PlantCareDocumentSearchResult(
                "바질", PlantCareSpeciesMatchType.CANONICAL_NAME, "empty-v1", List.of());
    PlantCareKnowledgeRetriever emptyRetriever =
        new OfficialDocumentPlantCareKnowledgeRetriever(normalizer, emptyMatchedCorpus);

    PlantCareKnowledge knowledge = emptyRetriever.retrieve(new PlantCareKnowledgeQuery("바질"));

    assertThat(knowledge.evidenceStatus()).isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
    assertThat(knowledge.resolvedSpeciesName()).isEqualTo("바질");
  }

  @Test
  void changesSourceContextHashWhenOfficialDocumentContentChanges() {
    PlantCareKnowledge before = retrieveFromEvidence("햇빛이 좋은 곳에서 기릅니다.");
    PlantCareKnowledge after = retrieveFromEvidence("햇빛과 통풍이 좋은 곳에서 기릅니다.");

    assertThat(before.sourceContextHash()).isNotEqualTo(after.sourceContextHash());
    assertThat(before.grounding().sources().getFirst().contentHash())
        .isNotEqualTo(after.grounding().sources().getFirst().contentHash());
  }

  @Test
  void rejectsNonHttpsEvidenceUrlBeforeItCanBeExposedToClients() {
    assertThatThrownBy(
            () ->
                new PlantCareEvidence(
                    "unsafe-source", "안전하지 않은 출처", "javascript:alert(1)", "v1", "검증할 수 없는 내용"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private PlantCareKnowledge retrieveFromEvidence(String content) {
    PlantCareDocumentCorpus corpus =
        query ->
            new PlantCareDocumentSearchResult(
                "바질",
                PlantCareSpeciesMatchType.CANONICAL_NAME,
                "test-corpus-v1",
                List.of(
                    new PlantCareEvidence(
                        "official-basil",
                        "공식 바질 재배 문서",
                        "https://example.test/basil",
                        "2026-08-21",
                        content)));
    return new OfficialDocumentPlantCareKnowledgeRetriever(normalizer, corpus)
        .retrieve(new PlantCareKnowledgeQuery("바질"));
  }
}
