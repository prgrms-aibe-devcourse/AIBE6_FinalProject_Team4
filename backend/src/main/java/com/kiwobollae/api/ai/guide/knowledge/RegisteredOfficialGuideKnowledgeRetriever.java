package com.kiwobollae.api.ai.guide.knowledge;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠 도메인이 관리하는 종별 공식 재배 가이드를 첫 번째 RAG 근거로 사용한다.
 *
 * <p>종이 요청 시점에 서버에서 확정돼 있으므로 의미 유사도보다 정확한 종 ID 조회를 우선한다. 외부 자료가 팀에서 검증되면 별도 구현체가 같은 계약으로 근거를 추가할 수
 * 있다.
 */
@Component
public class RegisteredOfficialGuideKnowledgeRetriever implements PlantCareKnowledgeRetriever {

  private static final String SOURCE_NAME = "서비스 등록 공식 재배 가이드";
  private static final String FALLBACK_VERSION = "등록일 미상";

  @Override
  public PlantCareKnowledge retrieve(PlantCareKnowledgeQuery query) {
    if (query == null || blank(query.officialGuide())) {
      return new PlantCareKnowledge(List.of());
    }

    String sourceId =
        query.speciesId() == null
            ? "plant-species:unknown:official-care-guide"
            : "plant-species:" + query.speciesId() + ":official-care-guide";
    return new PlantCareKnowledge(
        List.of(
            new PlantCareEvidence(
                sourceId,
                SOURCE_NAME,
                formatVersion(query.sourceUpdatedAt()),
                query.officialGuide().trim())));
  }

  private String formatVersion(LocalDateTime sourceUpdatedAt) {
    if (sourceUpdatedAt == null) {
      return FALLBACK_VERSION;
    }
    return sourceUpdatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
