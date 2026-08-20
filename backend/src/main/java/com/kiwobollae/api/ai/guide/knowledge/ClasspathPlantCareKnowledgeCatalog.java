package com.kiwobollae.api.ai.guide.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 팀이 검증해 저장소에 등록한 종별 재배 자료를 정확한 종명으로 조회한다. */
@Component
public class ClasspathPlantCareKnowledgeCatalog {

  private static final String RESOURCE_PATH = "ai/plant-care/verified-knowledge.json";

  private final Map<String, List<PlantCareEvidence>> evidenceBySpeciesName;

  public ClasspathPlantCareKnowledgeCatalog(ObjectMapper objectMapper) {
    this.evidenceBySpeciesName = load(objectMapper);
  }

  public List<PlantCareEvidence> findBySpeciesName(String speciesName) {
    return evidenceBySpeciesName.getOrDefault(normalize(speciesName), List.of());
  }

  private Map<String, List<PlantCareEvidence>> load(ObjectMapper objectMapper) {
    try (InputStream inputStream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      KnowledgeCatalogFile catalog = objectMapper.readValue(json, KnowledgeCatalogFile.class);
      if (catalog == null || catalog.documents() == null || catalog.documents().isEmpty()) {
        throw new IllegalStateException("검증 재배 자료가 비어 있습니다.");
      }

      Map<String, List<PlantCareEvidence>> result = new HashMap<>();
      for (KnowledgeDocument document : catalog.documents()) {
        PlantCareEvidence documentEvidence =
            new PlantCareEvidence(
                document.sourceId(),
                document.sourceName(),
                document.sourceUrl(),
                document.version(),
                document.content());
        if (document.speciesNames() == null || document.speciesNames().isEmpty()) {
          throw new IllegalStateException("검증 재배 자료에 대상 종명이 필요합니다.");
        }
        for (String speciesName : document.speciesNames()) {
          String key = normalize(speciesName);
          List<PlantCareEvidence> speciesEvidence =
              result.computeIfAbsent(key, ignored -> new ArrayList<>());
          if (speciesEvidence.stream()
              .anyMatch(item -> item.sourceId().equals(document.sourceId()))) {
            throw new IllegalStateException("같은 식물에 같은 재배 근거가 중복 등록되었습니다: " + speciesName);
          }
          speciesEvidence.add(documentEvidence);
        }
      }
      return result.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    } catch (IOException | JacksonException exception) {
      throw new IllegalStateException("검증 재배 자료를 읽을 수 없습니다.", exception);
    }
  }

  // PlantCareGuideService#normalizeSpeciesName과 동일하게 공백을 완전히 제거해야 "방울 토마토"처럼
  // 사용자가 띄어 쓴 입력도 "방울토마토" 문서와 매칭된다.
  private String normalize(String speciesName) {
    if (speciesName == null || speciesName.isBlank()) {
      throw new IllegalArgumentException("식물 종명이 필요합니다.");
    }
    return speciesName.replaceAll("\\s+", "");
  }

  public record KnowledgeCatalogFile(List<KnowledgeDocument> documents) {}

  public record KnowledgeDocument(
      List<String> speciesNames,
      String sourceId,
      String sourceName,
      String sourceUrl,
      String version,
      String content) {}
}
