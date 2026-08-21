package com.kiwobollae.api.ai.knowledge;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 캐시에 공식 문서 출처·버전·내용 해시를 저장하고 다시 읽는 공통 codec이다. */
@Component
public class PlantCareKnowledgeMetadataCodec {

  private static final TypeReference<List<PlantCareEvidenceSource>> SOURCE_LIST_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public PlantCareKnowledgeMetadataCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String serializeSources(PlantCareKnowledge knowledge) {
    return serializeSources(knowledge.grounding().sources());
  }

  public String serializeSources(List<PlantCareEvidenceSource> sources) {
    try {
      return objectMapper.writeValueAsString(List.copyOf(sources));
    } catch (JacksonException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  public PlantCareGrounding deserializeGrounding(
      PlantCareEvidenceStatus status,
      PlantCareEvidenceScope scope,
      String resolvedSpeciesName,
      String sourcesJson) {
    if (status == null && (sourcesJson == null || sourcesJson.isBlank())) {
      return PlantCareGrounding.fallback(resolvedSpeciesName);
    }
    if (status == null || scope == null || sourcesJson == null || sourcesJson.isBlank()) {
      throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }
    try {
      return new PlantCareGrounding(
          status,
          scope,
          resolvedSpeciesName,
          objectMapper.readValue(sourcesJson, SOURCE_LIST_TYPE));
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }
  }
}
