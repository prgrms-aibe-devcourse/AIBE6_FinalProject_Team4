package com.kiwobollae.api.ai.guide.dto;

import java.util.List;

/**
 * AI가 작성하는 재배 가이드 본문. 캐시에 저장되는 JSON이 정확히 이 shape다.
 *
 * <p>서버가 채우는 값({@code speciesName}·{@code cached})은 여기에 두지 않는다. 응답 record에 섞어두면 AI 응답에 없는 필드를
 * 역직렬화하게 되고, primitive 필드는 값이 없을 때 Jackson이 실패한다({@code FAIL_ON_NULL_FOR_PRIMITIVES}가 기본 활성). 서버 값이
 * 붙은 최종 응답은 {@link PlantCareGuide}다.
 *
 * <p>필드 이름은 {@link PlantCareGuideSchema}와 일치해야 한다 — AI 응답을 이 record로 바로 역직렬화한다.
 */
public record PlantCareGuideContent(
    String difficulty,
    String difficultyReason,
    Environment environment,
    List<Stage> stages,
    List<Pitfall> pitfalls,
    String harvestTarget) {

  /** 환경 요약 — 사용자가 자기 베란다·창가와 바로 비교할 수 있게 한 문장씩. */
  public record Environment(String sunlight, String watering, String temperature) {}

  /** 생육 단계별 관리법. */
  public record Stage(String name, String guide) {}

  /** 흔한 실패와 대처. */
  public record Pitfall(String problem, String action) {}
}
