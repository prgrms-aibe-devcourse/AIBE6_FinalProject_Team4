package com.kiwobollae.api.ai.guide.dto;

import com.kiwobollae.api.ai.guide.dto.PlantCareGuideContent.Environment;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideContent.Pitfall;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideContent.Stage;
import java.util.List;

/**
 * 종 하나에 대한 재배 가이드 응답.
 *
 * <p>{@code speciesName}·{@code cached}는 서버가 채우고, 나머지는 {@link PlantCareGuideContent}를 그대로 펼친 것이다.
 * 클라이언트가 한 겹 더 들어가지 않게 평평한 shape로 내려준다.
 *
 * <p>{@code cached=true}면 이전에 생성해 저장해 둔 가이드이고, 이 요청에서는 AI를 호출하지 않았다.
 */
public record PlantCareGuide(
    String speciesName,
    String difficulty,
    String difficultyReason,
    Environment environment,
    List<Stage> stages,
    List<Pitfall> pitfalls,
    String harvestTarget,
    boolean cached) {

  public static PlantCareGuide of(
      String speciesName, PlantCareGuideContent content, boolean cached) {
    return new PlantCareGuide(
        speciesName,
        content.difficulty(),
        content.difficultyReason(),
        content.environment(),
        content.stages(),
        content.pitfalls(),
        content.harvestTarget(),
        cached);
  }
}
